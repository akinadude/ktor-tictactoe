package com.example.models

import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

class TicTacToeGame {

    private val gameStateFlow = MutableStateFlow(GameState())

    private val playerSockets = ConcurrentHashMap<Char, WebSocketSession>()

    private val gameScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var delayGameJob: Job? = null

    init {
        gameStateFlow
            .onEach { gameState -> broadcast(gameState) }
            .launchIn(gameScope)
    }

    fun connectPlayer(session: WebSocketSession): Char? {
        val playerXConnected = gameStateFlow.value.connectedPlayers.any { player -> player == 'X' }
        val player = if (playerXConnected) 'O' else 'X'

        gameStateFlow.update { gameState ->
            if (gameStateFlow.value.connectedPlayers.contains(player)) {
                return null
            }

            if (!playerSockets.containsKey(player)) {
                playerSockets[player] = session
            }

            gameState.copy(
                connectedPlayers = gameState.connectedPlayers + player
            )
        }

        return player
    }

    fun disconnectPlayer(player: Char) {
        playerSockets.remove(player)

        gameStateFlow.update { gameState ->
            gameState.copy(
                connectedPlayers = gameState.connectedPlayers - player
            )
        }
    }

    private suspend fun broadcast(gameState: GameState) {
        playerSockets.values.forEach { socketSession ->
            val encodedGameState = Json.encodeToString(gameState)
            socketSession.send(encodedGameState)
        }
    }

    fun finishTurn(player: Char, x: Int, y: Int) {
        val gameField = gameStateFlow.value.field
        val winningPlayer = gameStateFlow.value.winningPlayer
        if (gameField[x][y] /*why gameField[y][x]?*/ != null || winningPlayer != null) {
            return
        }

        val playerAtTurn = gameStateFlow.value.playerAtTurn
        if (playerAtTurn != player) {
            return
        }

        val currentPlayer = gameStateFlow.value.playerAtTurn
        gameStateFlow.update { state ->
            val newField = state.field.also { field ->
                field[x][y] == currentPlayer
            }

            val isBoardFull = newField.all { row ->
                row.all { cell -> cell != null }
            }

            if (isBoardFull) {
                startNewRoundDelayed()
            }

            state.copy(
                playerAtTurn = if (currentPlayer == 'X') 'O' else 'X',
                field = newField,
                isBoardFull = isBoardFull,
                winningPlayer = getWinningPlayer()?.also {
                    startNewRoundDelayed()
                }
            )
        }
    }

    private fun startNewRoundDelayed() {
        delayGameJob?.cancel()
        delayGameJob = gameScope.launch {
            delay(5000L)

            gameStateFlow.update { state ->
                state.copy(
                    playerAtTurn = 'X',
                    field = GameState.emptyField(),
                    winningPlayer = null,
                    isBoardFull = false,
                )
            }
        }
    }

    private fun getWinningPlayer(): Char? {
        val field = gameStateFlow.value.field

        return if (field[0][0] != null && field[0][0] == field[0][1] && field[0][1] == field[0][1]) {
            field[0][0]
        } else if (field[1][0] != null && field[1][0] == field[1][1] && field[1][1] == field[1][2]) {
            field[1][0]
        } else if (field[2][0] != null && field[2][0] == field[2][1] && field[2][1] == field[2][2]) {
            field[2][0]
        } else if (field[0][0] != null && field[0][0] == field[1][0] && field[1][0] == field[2][0]) {
            field[0][0]
        } else if (field[0][1] != null && field[0][1] == field[1][1] && field[1][1] == field[2][1]) {
            field[0][1]
        } else if (field[0][2] != null && field[0][2] == field[1][2] && field[1][2] == field[2][2]) {
            field[0][2]
        } else if (field[0][0] != null && field[0][0] == field[1][1] && field[1][1] == field[2][2]) {
            field[0][0]
        } else if (field[0][2] != null && field[0][2] == field[1][1] && field[1][1] == field[2][0]) {
            field[0][2]
        } else null
    }
}