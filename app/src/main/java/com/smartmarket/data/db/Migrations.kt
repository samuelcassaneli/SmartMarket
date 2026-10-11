package com.smartmarket.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migrations {

    /** v2 (SmartMarket 1.x) para v3 (2.0): colunas novas e tabelas de notas, despensa, alertas e encartes. */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            MIGRATION_2_3_SQL.forEach { db.execSQL(it) }
            Seed.insertDefaultMarkets(db)
        }
    }

    /** v3 para v4: só dados. Liga os folhetos da página da loja do Atacadão de Resende. */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "UPDATE `supermarkets` SET `flyerUrl` = ?, `flyerKind` = 'ATACADAO_STORE' " +
                    "WHERE `onlineSource` = 'VTEX_ATACADAO' AND `flyerUrl` = ''",
                arrayOf<Any>(Seed.ATACADAO_RESENDE_URL)
            )
        }
    }

    val ALL = arrayOf(MIGRATION_2_3, MIGRATION_3_4)

    // Derivado de app/schemas/.../2.json e 3.json; o schema resultante foi conferido contra o 3.json.
    // As duas últimas instruções preenchem dados: data de criação e histórico a partir dos preços mensais.
    internal val MIGRATION_2_3_SQL = listOf(
        "ALTER TABLE `products` ADD COLUMN `imageUrl` TEXT NOT NULL DEFAULT ''",
        "ALTER TABLE `products` ADD COLUMN `isFavorite` INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE `products` ADD COLUMN `createdAt` INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE `supermarkets` ADD COLUMN `cnpj` TEXT NOT NULL DEFAULT ''",
        "ALTER TABLE `supermarkets` ADD COLUMN `city` TEXT NOT NULL DEFAULT ''",
        "ALTER TABLE `supermarkets` ADD COLUMN `onlineSource` TEXT NOT NULL DEFAULT 'NONE'",
        "ALTER TABLE `supermarkets` ADD COLUMN `flyerUrl` TEXT NOT NULL DEFAULT ''",
        "ALTER TABLE `supermarkets` ADD COLUMN `flyerKind` TEXT NOT NULL DEFAULT 'NONE'",
        "ALTER TABLE `product_prices` ADD COLUMN `source` TEXT NOT NULL DEFAULT 'MANUAL'",
        "ALTER TABLE `shopping_lists` ADD COLUMN `isArchived` INTEGER NOT NULL DEFAULT 0",
        "CREATE TABLE IF NOT EXISTS `price_observations` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `productId` INTEGER NOT NULL, `supermarketId` INTEGER NOT NULL, `price` REAL NOT NULL, `source` TEXT NOT NULL, `observedAt` INTEGER NOT NULL, `note` TEXT NOT NULL, FOREIGN KEY(`productId`) REFERENCES `products`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`supermarketId`) REFERENCES `supermarkets`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE INDEX IF NOT EXISTS `index_price_observations_productId_observedAt` ON `price_observations` (`productId`, `observedAt`)",
        "CREATE INDEX IF NOT EXISTS `index_price_observations_supermarketId` ON `price_observations` (`supermarketId`)",
        "CREATE TABLE IF NOT EXISTS `purchases` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `accessKey` TEXT, `supermarketId` INTEGER, `cnpj` TEXT NOT NULL, `storeName` TEXT NOT NULL, `storeAddress` TEXT NOT NULL, `issuedAt` INTEGER NOT NULL, `totalAmount` REAL NOT NULL, `discount` REAL NOT NULL, `paymentMethod` TEXT NOT NULL, `itemCount` INTEGER NOT NULL, `sourceUrl` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, FOREIGN KEY(`supermarketId`) REFERENCES `supermarkets`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_purchases_accessKey` ON `purchases` (`accessKey`)",
        "CREATE INDEX IF NOT EXISTS `index_purchases_supermarketId` ON `purchases` (`supermarketId`)",
        "CREATE INDEX IF NOT EXISTS `index_purchases_issuedAt` ON `purchases` (`issuedAt`)",
        "CREATE TABLE IF NOT EXISTS `purchase_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `purchaseId` INTEGER NOT NULL, `productId` INTEGER, `rawDescription` TEXT NOT NULL, `code` TEXT NOT NULL, `quantity` REAL NOT NULL, `unit` TEXT NOT NULL, `unitPrice` REAL NOT NULL, `totalPrice` REAL NOT NULL, FOREIGN KEY(`purchaseId`) REFERENCES `purchases`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`productId`) REFERENCES `products`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
        "CREATE INDEX IF NOT EXISTS `index_purchase_items_purchaseId` ON `purchase_items` (`purchaseId`)",
        "CREATE INDEX IF NOT EXISTS `index_purchase_items_productId` ON `purchase_items` (`productId`)",
        "CREATE TABLE IF NOT EXISTS `product_aliases` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `productId` INTEGER NOT NULL, `normalizedAlias` TEXT NOT NULL, `source` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, FOREIGN KEY(`productId`) REFERENCES `products`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_product_aliases_normalizedAlias` ON `product_aliases` (`normalizedAlias`)",
        "CREATE INDEX IF NOT EXISTS `index_product_aliases_productId` ON `product_aliases` (`productId`)",
        "CREATE TABLE IF NOT EXISTS `pantry_items` (`productId` INTEGER NOT NULL, `quantity` REAL NOT NULL, `minQuantity` REAL NOT NULL, `expiresAt` INTEGER, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`productId`), FOREIGN KEY(`productId`) REFERENCES `products`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE TABLE IF NOT EXISTS `price_alerts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `productId` INTEGER NOT NULL, `targetPrice` REAL, `notifyOnAnyDrop` INTEGER NOT NULL, `active` INTEGER NOT NULL, `lastNotifiedPrice` REAL, `createdAt` INTEGER NOT NULL, FOREIGN KEY(`productId`) REFERENCES `products`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_price_alerts_productId` ON `price_alerts` (`productId`)",
        "CREATE TABLE IF NOT EXISTS `flyers` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `supermarketId` INTEGER NOT NULL, `sourceUrl` TEXT NOT NULL, `contentHash` TEXT NOT NULL, `fetchedAt` INTEGER NOT NULL, `pageCount` INTEGER NOT NULL, `offerCount` INTEGER NOT NULL, `status` TEXT NOT NULL, `errorMessage` TEXT NOT NULL, FOREIGN KEY(`supermarketId`) REFERENCES `supermarkets`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_flyers_supermarketId_contentHash` ON `flyers` (`supermarketId`, `contentHash`)",
        "CREATE TABLE IF NOT EXISTS `flyer_offers` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `flyerId` INTEGER NOT NULL, `supermarketId` INTEGER NOT NULL, `productId` INTEGER, `rawText` TEXT NOT NULL, `price` REAL NOT NULL, `confidence` REAL NOT NULL, `status` TEXT NOT NULL, FOREIGN KEY(`flyerId`) REFERENCES `flyers`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`productId`) REFERENCES `products`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
        "CREATE INDEX IF NOT EXISTS `index_flyer_offers_flyerId` ON `flyer_offers` (`flyerId`)",
        "CREATE INDEX IF NOT EXISTS `index_flyer_offers_productId` ON `flyer_offers` (`productId`)",
        "UPDATE `products` SET `createdAt` = CAST(strftime('%s','now') AS INTEGER) * 1000",
        "INSERT INTO `price_observations` (`productId`, `supermarketId`, `price`, `source`, `observedAt`, `note`) SELECT `productId`, `supermarketId`, `price`, 'MANUAL', CAST(strftime('%s', printf('%04d-%02d-15 12:00:00', `referenceYear`, `referenceMonth`)) AS INTEGER) * 1000, '' FROM `product_prices`"
    )
}
