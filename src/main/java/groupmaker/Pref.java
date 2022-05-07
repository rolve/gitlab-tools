package groupmaker;

import static groupmaker.Pref.Strength.NONE;
import static java.util.Objects.hash;

class Pref {

    static Pref pref(Slot slot, Strength strength) {
        return new Pref(slot, strength);
    }

    final Slot slot;
    final Strength strength;

    private Pref(Slot slot, Strength strength) {
        this.slot = slot;
        this.strength = strength;
    }

    enum Strength {
        STRONG, WEAK, NONE
    }

    @Override
    public int hashCode() {
        return hash(slot, strength);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Pref 
                && slot == ((Pref) obj).slot
                && strength == ((Pref) obj).strength;
    }

    @Override
    public String toString() {
        if (strength == NONE) {
            return "none";
        } else {
            return strength.name().toLowerCase() + "ly " + slot.name; 
        }
    }
}
