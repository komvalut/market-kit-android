package io.horizontalsystems.marketkit.models

/**
 * Coin/blockchain definitions that must be available even when the remote MarketKit
 * backend does not (yet) serve them. They are merged into every coin sync and also
 * seeded on startup so privacy chains like Pirate Chain show up in the UI.
 *
 * To add a new privacy coin, extend [seed] with its blockchain, coin and native token.
 */
internal object LocalCoinCatalog {

    data class Seeds(
        val coins: List<Coin>,
        val blockchains: List<BlockchainEntity>,
        val tokens: List<TokenEntity>,
    )

    fun seed(): Seeds {
        val pirateChainUid = BlockchainType.PirateChain.uid

        val blockchain = BlockchainEntity(
            uid = pirateChainUid,
            name = "Pirate Chain",
            eip3091url = null,
        )

        val coin = Coin(
            uid = pirateChainUid,
            name = "Pirate Chain",
            code = "ARRR",
            marketCapRank = null,
            coinGeckoId = "pirate-chain",
            image = null,
        )

        val token = TokenEntity(
            coinUid = pirateChainUid,
            blockchainUid = pirateChainUid,
            type = "native",
            decimals = 8,
            reference = "",
        )

        return Seeds(
            coins = listOf(coin),
            blockchains = listOf(blockchain),
            tokens = listOf(token),
        )
    }
}
