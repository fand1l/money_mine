package dev.maximus.hryvnia.economy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.maximus.hryvnia.HryvniaMod;
import dev.maximus.hryvnia.network.ModPayloads;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side account book. Holds every player's balance, card number and
 * current job. Persisted as plain JSON inside the world folder so it survives
 * restarts and is easy for admins to inspect or fix by hand.
 */
public class EconomyState {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final SecureRandom RANDOM = new SecureRandom();

    private static EconomyState current;

    public static class Account {
        public long balance = 0;
        public String card = null;
        public String name = "";
        public String job = null;
    }

    /** uuid string -> account. */
    public Map<String, Account> accounts = new LinkedHashMap<>();

    private transient Map<String, UUID> byCard = new HashMap<>();
    private transient boolean dirty = false;
    private transient MinecraftServer server;

    public static EconomyState get() {
        return current;
    }

    public static void load(MinecraftServer server) {
        EconomyState state = null;
        Path path = filePath(server);
        if (Files.exists(path)) {
            try {
                state = GSON.fromJson(Files.readString(path), EconomyState.class);
            } catch (Exception e) {
                HryvniaMod.LOGGER.error("Failed to read {}, starting with empty economy", path, e);
            }
        }
        if (state == null) {
            state = new EconomyState();
        }
        if (state.accounts == null) {
            state.accounts = new LinkedHashMap<>();
        }
        state.byCard = new HashMap<>();
        for (Map.Entry<String, Account> entry : state.accounts.entrySet()) {
            Account account = entry.getValue();
            if (account != null && account.card != null) {
                state.byCard.put(account.card, UUID.fromString(entry.getKey()));
            }
        }
        state.server = server;
        current = state;
        HryvniaMod.LOGGER.info("Loaded hryvnia economy: {} accounts", state.accounts.size());
    }

    public static void unload() {
        if (current != null) {
            current.save();
            current = null;
        }
    }

    private static Path filePath(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("hryvnia_economy.json");
    }

    public void markDirty() {
        dirty = true;
    }

    public void saveIfDirty() {
        if (dirty) {
            save();
        }
    }

    public void save() {
        if (server == null) {
            return;
        }
        try {
            Path path = filePath(server);
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(this));
            dirty = false;
        } catch (IOException e) {
            HryvniaMod.LOGGER.error("Failed to save hryvnia economy", e);
        }
    }

    public Account account(UUID uuid) {
        return accounts.computeIfAbsent(uuid.toString(), k -> new Account());
    }

    public Account account(ServerPlayer player) {
        Account account = account(player.getUUID());
        account.name = player.getName().getString();
        return account;
    }

    public MinecraftServer server() {
        return server;
    }

    public long balance(UUID uuid) {
        return account(uuid).balance;
    }

    public void setBalance(UUID uuid, long value) {
        account(uuid).balance = Math.max(0, value);
        markDirty();
        syncBalance(uuid);
    }

    public void addBalance(UUID uuid, long delta) {
        setBalance(uuid, account(uuid).balance + delta);
    }

    /** Pushes the player's balance to their client for the card tooltip. */
    public void syncBalance(UUID uuid) {
        if (server == null) {
            return;
        }
        ServerPlayer online = server.getPlayerList().getPlayer(uuid);
        if (online != null) {
            ServerPlayNetworking.send(online, new ModPayloads.BalancePayload(account(uuid).balance));
        }
    }

    public UUID ownerOfCard(String cardNumber) {
        return byCard.get(cardNumber);
    }

    /** Returns the player's card number, generating a unique one on first use. */
    public String getOrCreateCard(ServerPlayer player) {
        Account account = account(player);
        if (account.card == null) {
            String number;
            do {
                number = generateCardNumber();
            } while (byCard.containsKey(number));
            account.card = number;
            byCard.put(number, player.getUUID());
            markDirty();
        }
        return account.card;
    }

    private static String generateCardNumber() {
        StringBuilder sb = new StringBuilder("5375");
        for (int i = 0; i < 12; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return format(sb.toString());
    }

    /** Formats a 16-digit number as "XXXX XXXX XXXX XXXX". */
    public static String format(String digits) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && i % 4 == 0) {
                sb.append(' ');
            }
            sb.append(digits.charAt(i));
        }
        return sb.toString();
    }

    /** Strips everything but digits and re-formats; returns null if not 16 digits. */
    public static String normalizeCardInput(String input) {
        String digits = input.replaceAll("[^0-9]", "");
        if (digits.length() != 16) {
            return null;
        }
        return format(digits);
    }

    public enum TransferResult {
        OK,
        NO_SUCH_CARD,
        NOT_ENOUGH_MONEY,
        SELF
    }

    public TransferResult transfer(ServerPlayer from, String cardNumber, long amount) {
        UUID to = ownerOfCard(cardNumber);
        if (to == null) {
            return TransferResult.NO_SUCH_CARD;
        }
        if (to.equals(from.getUUID())) {
            return TransferResult.SELF;
        }
        Account sender = account(from);
        if (sender.balance < amount) {
            return TransferResult.NOT_ENOUGH_MONEY;
        }
        sender.balance -= amount;
        account(to).balance += amount;
        markDirty();
        syncBalance(from.getUUID());
        syncBalance(to);
        return TransferResult.OK;
    }

    public String nameOf(UUID uuid) {
        Account account = accounts.get(uuid.toString());
        return account != null && account.name != null && !account.name.isEmpty() ? account.name : uuid.toString().substring(0, 8);
    }
}
