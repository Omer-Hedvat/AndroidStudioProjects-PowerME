package com.omerhedvat.powerme.actions

/**
 * Represents the result of executing an ActionBlock.
 *
 * Used to provide feedback to the user about whether the action succeeded or failed.
 */
sealed class ActionResult {
    /**
     * Action executed successfully.
     *
     * @param message User-friendly success message
     */
    data class Success(val message: String) : ActionResult()

    /**
     * Action execution failed.
     *
     * @param error User-friendly error message explaining what went wrong
     */
    data class Failure(val error: String) : ActionResult()
}
