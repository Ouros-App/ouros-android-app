package com.ouros.app.feature.main

/**
 * Modelos de dados de main. Os tipos abaixo espelham os campos usados no contrato da API ou no fluxo local; os nomes serializados mostram como cada propriedade é convertida para JSON.
 */
data class MainHeaderUiModel(
    val farmOwnerId: Long?,
    val name: String,
    val photoUrl: String?
)