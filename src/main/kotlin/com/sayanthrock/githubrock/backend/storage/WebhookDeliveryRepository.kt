package com.sayanthrock.githubrock.backend.storage

import javax.sql.DataSource

class WebhookDeliveryRepository(private val dataSource: DataSource) {
    fun register(deliveryId: String, eventName: String): Boolean = dataSource.connection.use { connection ->
        connection.prepareStatement(
            """
            INSERT INTO webhook_deliveries(delivery_id, event_name)
            VALUES (?, ?)
            ON CONFLICT (delivery_id) DO NOTHING
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, deliveryId)
            statement.setString(2, eventName)
            statement.executeUpdate() == 1
        }
    }
}
