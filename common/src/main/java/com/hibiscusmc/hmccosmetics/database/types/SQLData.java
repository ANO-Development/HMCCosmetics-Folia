package com.hibiscusmc.hmccosmetics.database.types;

import com.hibiscusmc.hmccosmetics.HMCCosmeticsPlugin;
import com.hibiscusmc.hmccosmetics.cosmetic.Cosmetic;
import com.hibiscusmc.hmccosmetics.cosmetic.CosmeticSlot;
import com.hibiscusmc.hmccosmetics.database.Database;
import com.hibiscusmc.hmccosmetics.database.UserData;
import com.hibiscusmc.hmccosmetics.user.CosmeticUser;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public abstract class SQLData extends Data {
    @Override
    @SuppressWarnings({"resource"}) // Duplicate is from deprecated InternalData
    public CompletableFuture<UserData> get(UUID uniqueId) {
        return Database.supply(() -> {
            UserData data = new UserData(uniqueId);

            try (PreparedStatement preparedStatement = preparedStatement("SELECT * FROM COSMETICDATABASE WHERE UUID = ?;")){
                preparedStatement.setString(1, uniqueId.toString());
                try (ResultSet rs = preparedStatement.executeQuery()) {
                    if (rs.next()) {
                        String rawData = rs.getString("COSMETICS");
                        data = deserializeUserData(uniqueId, rawData);
                    }
                }
            } catch (SQLException exception) {
                throw new IllegalStateException("Unable to load cosmetic data for " + uniqueId + ".", exception);
            }
            return data;
        });
    }

    @Override
    @SuppressWarnings("resource")
    public void save(CosmeticUser user) {
        save(UserData.snapshot(user));
    }

    @Override
    public void save(UserData userData) {
        UserData snapshot = new UserData(userData);
        Runnable run = () -> {
            try (PreparedStatement preparedSt = preparedStatement("REPLACE INTO COSMETICDATABASE(UUID,COSMETICS) VALUES(?,?);")) {
                preparedSt.setString(1, snapshot.getOwner().toString());
                preparedSt.setString(2, serializeData(snapshot));
                preparedSt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        };
        Database.execute(run).exceptionally(exception -> {
            HMCCosmeticsPlugin.getInstance().getLogger().log(java.util.logging.Level.SEVERE, "Unable to save cosmetic data for " + snapshot.getOwner() + ".", exception);
            return null;
        });
    }

    public abstract PreparedStatement preparedStatement(String query);
}
