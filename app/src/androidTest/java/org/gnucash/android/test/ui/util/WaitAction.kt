package org.gnucash.android.test.ui.util

import android.os.SystemClock
import android.view.View
import androidx.annotation.IdRes
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.actionWithAssertions
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.util.TreeIterables
import org.hamcrest.Matcher

class WaitAction(@IdRes private val viewId: Int, private val timeout: Long = WAIT) : ViewAction {
    override fun getConstraints(): Matcher<View> {
        return isDisplayed()
    }

    override fun getDescription(): String {
        return "Wait for a view"
    }

    override fun perform(uiController: UiController, view: View) {
        uiController.loopMainThreadUntilIdle()
        val endTime = SystemClock.elapsedRealtime() + timeout
        val viewMatcher: Matcher<View?> = withId(viewId)

        do {
            for (child in TreeIterables.breadthFirstViewTraversal(view)) {
                // found view with required ID
                if (viewMatcher.matches(child)) {
                    return
                }
            }

            uiController.loopMainThreadForAtLeast(WAIT)
        } while (SystemClock.elapsedRealtime() < endTime)
    }

    companion object {
        private const val WAIT = 50L

        fun waitForView(@IdRes viewId: Int): ViewAction {
            return actionWithAssertions(
                WaitAction(viewId)
            )
        }
    }
}
