package com.squidgame.client;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

/** Deslizador genérico para los ajustes. */
public class ValueSlider extends AbstractSliderButton {
    private final String label;
    private final double min;
    private final double max;
    private final DoubleConsumer setter;
    private final DoubleFunction<String> format;

    public ValueSlider(int x, int y, int w, int h, String label, double min, double max, double current,
                       DoubleFunction<String> format, DoubleConsumer setter) {
        super(x, y, w, h, Component.empty(), (current - min) / (max - min));
        this.label = label;
        this.min = min;
        this.max = max;
        this.format = format;
        this.setter = setter;
        this.updateMessage();
    }

    private double real() {
        return min + this.value * (max - min);
    }

    @Override
    protected void updateMessage() {
        this.setMessage(Component.literal(label + ": " + format.apply(real())));
    }

    @Override
    protected void applyValue() {
        setter.accept(real());
    }
}
