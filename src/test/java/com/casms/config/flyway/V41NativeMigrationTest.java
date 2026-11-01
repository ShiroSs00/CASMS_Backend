package com.casms.config.flyway;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Retains every created DB. No clean, repair, history edits or existing datasource fallback. */
@EnabledIfEnvironmentVariable(named="OCVVIET_V4_TEST",matches="true")
class V41NativeMigrationTest {
    private Connection connect(String db) throws SQLException {
        return DriverManager.getConnection("jdbc:postgresql://127.0.0.1:15436/"+db,"ocvviet_test","ocvviet_test_only_20261028");
    }
    private String database() throws SQLException {
        String db="ocvviet_v41_native_"+UUID.randomUUID().toString().replace("-","");
        try(var c=connect("postgres");var statement=c.createStatement()){statement.execute("CREATE DATABASE "+db);}
        System.out.println("V41 retained migration DB: "+db);return db;
    }
    private Flyway flyway(String db,String target){
        var config=Flyway.configure().dataSource("jdbc:postgresql://127.0.0.1:15436/"+db,"ocvviet_test","ocvviet_test_only_20261028")
            .locations("classpath:db/migration").cleanDisabled(true);
        if(target!=null)config.target(target);
        return config.load();
    }
    private long count(Connection c,String sql)throws SQLException{try(var s=c.createStatement();var r=s.executeQuery(sql)){assertTrue(r.next());return r.getLong(1);}}
    @Test void canonicalV24FreshAndUpgradePreserveRowsAndSequenceState()throws Exception{
        String fresh=database();var all=flyway(fresh,null);all.migrate();all.validate();
        assertEquals("65",all.info().current().getVersion().getVersion());
        String upgrade=database();flyway(upgrade,"54").migrate();
        long users,cruises;long sequence;
        try(var c=connect(upgrade)){
            users=count(c,"SELECT count(*) FROM \"User\"");cruises=count(c,"SELECT count(*) FROM \"Cruise\"");
            count(c,"SELECT setval('casms_cruise_code_seq',4321,true)");sequence=count(c,"SELECT last_value FROM casms_cruise_code_seq");
            assertEquals(1,count(c,"SELECT count(*) FROM flyway_schema_history WHERE version='24' AND script='V24__add_model_import_jobs_and_issues.sql' AND checksum=-562658990 AND success"));
        }
        var migrated=flyway(upgrade,null);migrated.migrate();migrated.validate();
        try(var c=connect(upgrade)){
            assertEquals(users,count(c,"SELECT count(*) FROM \"User\""));assertEquals(cruises,count(c,"SELECT count(*) FROM \"Cruise\""));
            assertEquals(cruises,count(c,"SELECT count(*) FROM \"Cruise\" WHERE preparation_coordinator_id IS NULL"));
            assertEquals(0,count(c,"SELECT count(*) FROM \"CruisePreparationEvent\""));
            assertEquals(sequence,count(c,"SELECT last_value FROM casms_cruise_code_seq"));
            assertEquals(1,count(c,"SELECT count(*) FROM flyway_schema_history WHERE version='63' AND script='V63__restore_missing_business_code_sequences.sql' AND success"));
            assertEquals(2,count(c,"SELECT count(*) FROM information_schema.tables WHERE table_name IN ('ModelImportJob','ModelImportIssue')"));
            assertTrue(count(c,"SELECT count(*) FROM pg_sequences WHERE schemaname='public' AND sequencename LIKE 'casms_%'")>=11);
            long history=count(c,"SELECT count(*) FROM flyway_schema_history");assertEquals(0,migrated.migrate().migrationsExecuted);
            assertEquals(history,count(c,"SELECT count(*) FROM flyway_schema_history"));
        }
    }
}
