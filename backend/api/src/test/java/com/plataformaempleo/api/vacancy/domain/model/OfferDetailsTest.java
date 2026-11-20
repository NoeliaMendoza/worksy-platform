package com.plataformaempleo.api.vacancy.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.plataformaempleo.common.domain.DomainException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class OfferDetailsTest {
    private OfferDetails offer(String min, String max, String currency) {
        return new OfferDetails(OfferDetails.ContractType.INDEFINITE, OfferDetails.WorkSchedule.FULL_TIME,
                " Tecnología ", null, "Desarrollar servicios", "Experiencia Java", null, null, null, null,
                min == null ? null : new BigDecimal(min), max == null ? null : new BigDecimal(max), currency,
                currency == null ? null : OfferDetails.SalaryPeriod.MONTH);
    }
    @Test void salarioOpcionalYTextoNormalizado() {
        var value = offer(null, null, null);
        value.requirePublishable();
        assertThat(value.area()).isEqualTo("Tecnología");
        assertThat(value.salaryMin()).isNull();
    }
    @Test void aceptaRangoYMoneda() {
        assertThat(offer("1200.00", "2000.00", "USD").salaryMax()).isEqualByComparingTo("2000");
    }
    @Test void rechazaRangosIncompletosInvertidosONegativos() {
        assertThatThrownBy(() -> offer("2000", "1200", "USD")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> offer("-1", "1200", "USD")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> offer(null, "1200", "USD")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> offer("1.001", "1200", "USD")).isInstanceOf(DomainException.class);
    }
    @Test void rechazaMonedaDesconocida() {
        assertThatThrownBy(() -> offer("1000", "1200", "ZZZ")).isInstanceOf(DomainException.class);
    }
    @Test void borradorIncompletoNoSePublica() {
        var draft = new OfferDetails(null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        assertThatThrownBy(draft::requirePublishable).isInstanceOf(DomainException.class);
    }
}
