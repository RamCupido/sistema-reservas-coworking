package com.ramses.reservas;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class ReservasApplicationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void contextLoads() {
    }

    @Test
    void flywayHabilitaLaExtensionBtreeGist() {
        Integer total = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_extension WHERE extname = 'btree_gist'", Integer.class);

        assertThat(total).isEqualTo(1);
    }
}
