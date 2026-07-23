package dev.maximus.hryvnia.client;

/**
 * Client-side mirror of the player's account balance, fed by BalancePayload.
 * Used by the bank screen and the card tooltip.
 */
public final class ClientEconomy {
    public static volatile long balance = 0;

    private ClientEconomy() {
    }
}
