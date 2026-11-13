package db.migration;

import in.anytimework.atwbackend.shared.security.PiiEncryptor;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One-time data migration for the "stop encrypting addresses" decision (confirmed with the developer
 * 2026-11-13, see {@code Address}'s Javadoc): decrypts every already-encrypted {@code worker_address}/
 * {@code admin_user_address} {@code line1}/{@code line2} value and rewrites it as plain text, so any
 * row created before this change matches what {@code WorkerAddress}/{@code AdminUserAddress} now read
 * and write unencrypted (their {@code @Convert} declarations were removed in the same change). A plain
 * SQL migration cannot do this - decryption needs the AES-GCM key and algorithm, which live in Java,
 * not SQL - so this is a Flyway {@link BaseJavaMigration}, discovered via classpath scanning of this
 * {@code db.migration} package (the Java-migration counterpart to {@code spring.flyway.locations=
 * classpath:db/migration}'s SQL scanning).
 *
 * <p>Reads the same environment variables Spring's own property placeholders resolve
 * ({@code WORKER_PII_ENCRYPTION_KEY}/{@code PII_ENCRYPTION_KEY}) directly via {@link System#getenv},
 * since Flyway runs as part of the same application startup, in the same process, before this
 * migration would ever execute.</p>
 */
public class V30__decrypt_existing_addresses extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        decryptTable(context, "worker_address", "WORKER_PII_ENCRYPTION_KEY");
        decryptTable(context, "admin_user_address", "PII_ENCRYPTION_KEY");
    }

    private void decryptTable(Context context, String table, String keyEnvVar) throws Exception {
        String base64Key = System.getenv(keyEnvVar);
        if (base64Key == null || base64Key.isBlank()) {
            // No key was ever configured in this environment, so nothing in this table could have
            // been encrypted in the first place - nothing to do.
            return;
        }
        PiiEncryptor encryptor = new PiiEncryptor(base64Key, keyEnvVar);

        List<AddressRow> rows = new ArrayList<>();
        try (Statement select = context.getConnection().createStatement();
             ResultSet rs = select.executeQuery("select id, line1, line2 from " + table)) {
            while (rs.next()) {
                rows.add(new AddressRow((UUID) rs.getObject("id"), rs.getString("line1"), rs.getString("line2")));
            }
        }

        try (PreparedStatement update = context.getConnection()
                .prepareStatement("update " + table + " set line1 = ?, line2 = ? where id = ?")) {
            for (AddressRow row : rows) {
                update.setString(1, tryDecrypt(encryptor, row.line1()));
                update.setString(2, tryDecrypt(encryptor, row.line2()));
                update.setObject(3, row.id());
                update.addBatch();
            }
            if (!rows.isEmpty()) {
                update.executeBatch();
            }
        }
    }

    /**
     * Null/blank passes through unchanged. A value that fails to decrypt is assumed to already be
     * plain text (e.g. a row inserted by test code that ran after the {@code @Convert} removal, in a
     * database that mixes pre- and post-change rows) and is left exactly as it was, never treated as
     * an error - this migration's only job is fixing genuinely-encrypted leftovers, never corrupting
     * a value that was never encrypted to begin with. Worst case for a row this heuristic gets wrong
     * (e.g. the wrong key for that table) is "left unchanged", never "corrupted".
     */
    private static String tryDecrypt(PiiEncryptor encryptor, String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        try {
            return encryptor.decrypt(value);
        } catch (RuntimeException notActuallyCiphertext) {
            return value;
        }
    }

    private record AddressRow(UUID id, String line1, String line2) {
    }
}
