package com.example.tusumba.ui.screens

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.tusumba.data.events.EventFriend
import com.example.tusumba.data.events.model.EventDetail
import com.example.tusumba.data.events.model.EventStatus
import com.example.tusumba.data.events.model.Money
import com.example.tusumba.data.events.model.Organizer
import com.example.tusumba.data.ticketing.model.Registration
import com.example.tusumba.data.ticketing.model.RegistrationSource
import com.example.tusumba.data.ticketing.model.RegistrationStatus
import com.example.tusumba.data.ticketing.model.Visibility
import com.example.tusumba.ui.registrations.todayForRegistrations
import java.time.OffsetDateTime
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ru-rRU")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EventDetailScreenTest {
    @get:Rule val compose = createComposeRule()
    private val event =
        EventDetail(
            id = UUID.randomUUID(),
            title = "Научный лекторий",
            date = todayForRegistrations().plusDays(1),
            placeAddress = "Новосибирск, ул. Пирогова, 1",
            isFree = true,
            status = EventStatus.confirmed,
            description = "Открытая лекция о науке",
            organizer = Organizer(Organizer.Type.club, clubName = "Научный клуб"),
            currentVersionId = UUID.randomUUID(),
            versionNo = 1,
        )
    private val loaded = EventDetailContent.Loaded(event, organizerName = "Научный клуб")

    @Test
    fun detailsShowOrganizerAndOpenMapsWithTheExactAddress() {
        var address = ""
        show(loaded, onMap = { address = it })
        compose.onNodeWithText(event.title).assertIsDisplayed()
        compose.onNodeWithText("Бесплатно").assertIsDisplayed()
        compose.onNodeWithText("Организатор: Научный клуб").assertIsDisplayed()
        compose.onNodeWithText(event.description).assertIsDisplayed()
        compose.onNodeWithTag("event_open_map").performScrollTo().performClick()
        assertEquals(event.placeAddress, address)
    }

    @Test
    fun guestGetsSignInPromptAndNoAnonymousSignupButton() {
        var signIn = false
        show(loaded, authenticated = false, onSignIn = { signIn = true })
        compose
            .onNodeWithText("Войдите, чтобы сохранить мероприятие в личном кабинете.")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("Войти").performScrollTo().performClick()
        assertTrue(signIn)
        compose.onNodeWithText("Анонимно").assertDoesNotExist()
        compose.onNodeWithText("Пойду").assertDoesNotExist()
        compose.onNodeWithText("Мои записи").assertDoesNotExist()
    }

    @Test
    fun everyVisibilityIsSelectableAndBothActionsWork() {
        val value = mutableStateOf(loaded)
        val statuses = mutableListOf<RegistrationStatus>()
        compose.setContent {
            EventDetailScreenContent(
                value.value,
                true,
                {},
                {},
                {},
                {},
                {},
                { statuses += it },
                { value.value = value.value.copy(visibility = it) },
                {},
            )
        }
        for (visibility in Visibility.entries) {
            compose
                .onNodeWithTag("event_visibility_${visibility.value}")
                .performScrollTo()
                .performClick()
                .assertIsSelected()
            assertEquals(visibility, value.value.visibility)
        }
        compose.onNodeWithText("Пойду").performScrollTo().performClick()
        compose.onNodeWithText("Интересно").performScrollTo().performClick()
        assertEquals(listOf(RegistrationStatus.going, RegistrationStatus.interested), statuses)
    }

    @Test
    fun currentAnonymousRegistrationIsShownWithAnOfferToChangeIt() {
        val registration =
            Registration(
                UUID.randomUUID(),
                event.id,
                RegistrationStatus.interested,
                Visibility.anonymous,
                RegistrationSource.free_signup,
                OffsetDateTime.parse("2026-11-11T06:00:00Z"),
            )
        show(loaded.copy(registration = registration, visibility = Visibility.anonymous))
        compose
            .onNodeWithText("Вы уже записаны на это мероприятие.")
            .performScrollTo()
            .assertIsDisplayed()
        compose
            .onNodeWithText("Сейчас: «Интересно», видимость — «Анонимно».")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithTag("event_visibility_anonymous").performScrollTo().assertIsSelected()
    }

    @Test
    fun savingDisablesActionsAndVisibilityUntilResponse() {
        show(loaded.copy(saving = true))
        compose.onNodeWithText("Пойду").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Интересно").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("event_visibility_anonymous").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Мои записи").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun paidEventShowsPriceAndUnavailabilityOfTicketPurchase() {
        show(
            loaded.copy(
                event = event.copy(isFree = false, price = Money(125050, Money.Currency.RUB))
            )
        )
        compose.onNodeWithText("1250,50 ₽").assertIsDisplayed()
        compose.onNodeWithText("Купить билет").performScrollTo().assertIsNotEnabled()
        compose
            .onNodeWithText("Покупка билетов пока недоступна.")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("Пойду").assertDoesNotExist()
    }

    @Test
    fun closedEventDoesNotOfferSaving() {
        show(loaded.copy(event = event.copy(date = todayForRegistrations().minusDays(1))))
        compose
            .onNodeWithText("Запись на мероприятие закрыта.")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("Пойду").assertDoesNotExist()
    }

    @Test
    fun registrationLookupFailureHasItsOwnRetryAndDoesNotEnableSaving() {
        var retried = false
        show(loaded.copy(registrationLoadFailed = true), onRegistrationRetry = { retried = true })
        compose
            .onNodeWithText("Не удалось загрузить вашу запись. Попробуйте позже.")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("Повторить").performScrollTo().performClick()
        assertTrue(retried)
        compose.onNodeWithText("Пойду").assertDoesNotExist()
    }

    @Test
    fun registrationLoadingKeepsTheEventVisibleWithoutEditableControls() {
        show(loaded.copy(loadingRegistration = true))
        compose.onNodeWithText(event.title).assertIsDisplayed()
        compose.onNodeWithText("Загрузка вашей записи…").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("event_visibility_public").assertDoesNotExist()
        compose.onNodeWithText("Пойду").assertDoesNotExist()
    }

    @Test
    fun saveErrorKeepsSelectionAndAllowsRetry() {
        show(
            loaded.copy(
                visibility = Visibility.anonymous,
                saveError = EventRegistrationError.Failed,
            )
        )
        compose.onNodeWithTag("event_visibility_anonymous").performScrollTo().assertIsSelected()
        compose
            .onNodeWithText("Не удалось сохранить запись. Попробуйте позже.")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("Пойду").performScrollTo().assertIsEnabled()
    }

    @Test
    fun availableFriendsAndMyRegistrationsNavigationAreShown() {
        var opened = false
        show(
            loaded.copy(
                friends = listOf(EventFriend(UUID.randomUUID(), "Саша", RegistrationStatus.going))
            ),
            onRegistrations = { opened = true },
        )
        compose.onNodeWithText("Друзья на мероприятии").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Саша — Пойду").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Мои записи").performScrollTo().performClick()
        assertTrue(opened)
    }

    @Test
    fun emptyFriendsDoNotCreateAnEmptySection() {
        show(loaded)
        compose.onNodeWithText("Друзья на мероприятии").assertDoesNotExist()
    }

    @Test
    fun eventLoadingKeepsBackNavigationAvailable() {
        var back = false
        show(EventDetailContent.Loading, onBack = { back = true })
        compose.onNodeWithText("Загрузка мероприятия…").assertIsDisplayed()
        compose.onNodeWithText("Назад").performClick()
        assertTrue(back)
    }

    @Test
    fun pageErrorHasRequiredMessageAndRetry() {
        var retry = false
        show(EventDetailContent.Error, onRetry = { retry = true })
        compose
            .onNodeWithText("Не удалось загрузить страницу мероприятия. Попробуйте позже.")
            .assertIsDisplayed()
        compose.onNodeWithText("Повторить").performClick()
        assertTrue(retry)
    }

    @Test
    fun unavailableEventHasRequiredMessageAndNoRegistrationControls() {
        show(EventDetailContent.Unavailable)
        compose.onNodeWithText("мероприятие недоступно.").assertIsDisplayed()
        compose.onNodeWithText("Пойду").assertDoesNotExist()
    }

    private fun show(
        state: EventDetailContent,
        authenticated: Boolean = true,
        onMap: (String) -> Unit = {},
        onSignIn: () -> Unit = {},
        onRegistrations: () -> Unit = {},
        onRegistrationRetry: () -> Unit = {},
        onBack: () -> Unit = {},
        onRetry: () -> Unit = {},
    ) {
        compose.setContent {
            EventDetailScreenContent(
                state,
                authenticated,
                onBack,
                onSignIn,
                onRegistrations,
                onRetry,
                onRegistrationRetry,
                {},
                {},
                onMap,
            )
        }
    }
}
