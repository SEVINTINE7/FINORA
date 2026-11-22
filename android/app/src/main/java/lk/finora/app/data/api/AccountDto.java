package lk.finora.app.data.api;

import java.math.BigDecimal;

public record AccountDto(long id, String name, String type, BigDecimal openingBalance, boolean archived) {
    public record Write(String name, String type, BigDecimal openingBalance) {}
}
