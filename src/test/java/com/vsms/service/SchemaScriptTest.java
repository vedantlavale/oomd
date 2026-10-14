package com.vsms.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vsms.VsmsApplication;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Proves that database/02-schema.sql matches the JPA mappings: the script creates the tables in an empty
 * database and Hibernate is then started with ddl-auto=validate, which fails if any table, column or type
 * differs. (The same check was also done by hand on MySQL 8.4.)
 */
@SpringBootTest(classes = VsmsApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:schemacheck;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=file:database/02-schema.sql",
        "vsms.sample-data.enabled=false"
})
@ActiveProfiles("test")
class SchemaScriptTest {

    @Autowired
    EntityManager entityManager;

    @Test
    void theSchemaScriptMatchesTheEntityMappings() {
        // If validation had failed the application context would not have started at all.
        Number tables = (Number) entityManager.createNativeQuery(
                "select count(*) from information_schema.tables where table_schema = 'public' "
                        + "and table_name in ('customers','vehicles','appointments','mechanics','service_jobs','service_tasks',"
                        + "'service_catalog_items','spare_parts','part_usages','invoices','payments','user_accounts')")
                .getSingleResult();
        assertTrue(tables.intValue() == 12, "expected the 12 application tables, found " + tables);
    }
}
