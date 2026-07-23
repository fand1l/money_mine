package dev.maximus.hryvnia.client.screen;

import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Small helper so screens can instantiate plain buttons directly. */
public class SimpleButton extends Button.Plain {
    public SimpleButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
    }
}
