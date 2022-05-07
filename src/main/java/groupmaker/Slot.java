package groupmaker;

class Slot {
    final String name;
    final int groups;

    Slot(String name, int groups) {
        this.name = name;
        this.groups = groups;
    }
    
    @Override
    public String toString() {
        return name + " (" + groups + ")";
    }
}
