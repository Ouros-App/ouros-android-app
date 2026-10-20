package com.ouros.app.data.goals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalsDtosTest {

    @Test
    fun goalResponse_isCompleted_checksStatusCorrectly() {
        val completedGoal = GoalResponse(
            id = 1L,
            title = "Meta 1",
            description = "Desc",
            status = GoalResponse.COMPLETED
        )
        assertTrue(completedGoal.isCompleted)

        val pendingGoal = completedGoal.copy(status = GoalResponse.PENDING)
        assertFalse(pendingGoal.isCompleted)

        val otherGoal = completedGoal.copy(status = "IN_PROGRESS")
        assertFalse(otherGoal.isCompleted)
    }

    @Test
    fun goalsUiState_progressAndCountsCalculatedCorrectly() {
        val emptyState = GoalsUiState()
        assertEquals(0, emptyState.totalGoals)
        assertEquals(0, emptyState.completedGoals)
        assertEquals(0, emptyState.progress)
        assertTrue(emptyState.allGoals.isEmpty())

        val goal1 = GoalResponse(1L, "G1", "D1", status = GoalResponse.COMPLETED)
        val goal2 = GoalResponse(2L, "G2", "D2", status = GoalResponse.PENDING)
        val goal3 = GoalResponse(3L, "G3", "D3", status = GoalResponse.COMPLETED)

        val state = GoalsUiState(
            personalGoals = listOf(goal1, goal2),
            stateGoals = listOf(goal3)
        )

        assertEquals(3, state.totalGoals)
        assertEquals(2, state.completedGoals)
        assertEquals(67, state.progress)
        assertEquals(listOf(goal1, goal2, goal3), state.allGoals)
    }

    @Test
    fun requestsAndKeys_propertiesWorkCorrectly() {
        val createRequest = CreateIndividualGoalRequest(
            title = "Nova Meta",
            description = "Descrição",
            farmId = 10L
        )
        assertEquals("Nova Meta", createRequest.title)
        assertEquals("Descrição", createRequest.description)
        assertEquals(10L, createRequest.farmId)

        val updateRequest = UpdateIndividualGoalRequest(
            title = "Meta Editada",
            description = "Nova Descrição",
            status = "COMPLETED"
        )
        assertEquals("Meta Editada", updateRequest.title)
        assertEquals("Nova Descrição", updateRequest.description)
        assertEquals("COMPLETED", updateRequest.status)

        val updateStatusRequest = UpdateGoalStatusRequest("PENDING")
        assertEquals("PENDING", updateStatusRequest.status)

        val key = GoalKey(GoalOrigin.PERSONAL, 42L)
        assertEquals(GoalOrigin.PERSONAL, key.origin)
        assertEquals(42L, key.id)

        val payload = GoalsPayload(
            listOf(createRequest.title).map {
                GoalResponse(1L, it, null, status = GoalResponse.PENDING)
            },
            emptyList()
        )
        assertEquals(1, payload.personalGoals.size)
        assertTrue(payload.stateGoals.isEmpty())
    }
}
