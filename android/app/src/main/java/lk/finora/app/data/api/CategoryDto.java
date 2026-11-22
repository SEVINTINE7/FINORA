package lk.finora.app.data.api;

public record CategoryDto(long id, String name, String nameSi, String kind, String needOrWant,
                          boolean isDefault) {
    public record Create(String name, String nameSi, String kind, String needOrWant) {}
    public record Update(String name, String nameSi, String needOrWant) {}
}
