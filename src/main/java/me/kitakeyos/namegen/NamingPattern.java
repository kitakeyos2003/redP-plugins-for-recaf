package me.kitakeyos.namegen;

import me.coley.recaf.control.Controller;
import me.kitakeyos.RedPlugin;
import me.coley.recaf.util.StringUtil;

/**
 * Types of naming patterns.
 *
 * @author Matt Coley
 */
public enum NamingPattern {
    INTELLIGENT,
    SOURCE_FILE,
    SIMPLE,
    ARABIC;

    @Override
    public String toString() {
        switch (this) {
            case INTELLIGENT:
                return "Intelligent";
            case SOURCE_FILE:
                return "Match source file";
            case SIMPLE:
                return "Simple";
            case ARABIC:
                return "Arabic";
            default:
                return StringUtil.toString(this);
        }
    }

    /**
     * @param controller The controller to pull classes from.
     * @param plugin Plugin instance to pull config from.
     *
     * @return A naming strategy to create appropriate names for items.
     */
    public NameStrategy createStrategy(Controller controller, RedPlugin plugin) {
        switch (this) {
            case INTELLIGENT:
                double classificationThreshold = plugin.intelligentGuessThreshold / 100.0;
                return new IntelligentStrategy(controller, classificationThreshold);
            case SOURCE_FILE:
                return new SourceFileStrategy(controller);
            case SIMPLE:
                return new SimpleStrategy(controller);
            case ARABIC:
                return new ArabicStrategy(controller);
            default:
                throw new UnsupportedOperationException("Unsupported naming pattern: " + name());
        }
    }
}
