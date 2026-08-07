SET @model_call_trace_language_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'model_call_trace'
      AND column_name = 'language'
);

SET @drop_model_call_trace_language = IF(
    @model_call_trace_language_exists > 0,
    'ALTER TABLE `model_call_trace` DROP COLUMN `language`',
    'SELECT 1'
);

PREPARE drop_model_call_trace_language_statement FROM @drop_model_call_trace_language;
EXECUTE drop_model_call_trace_language_statement;
DEALLOCATE PREPARE drop_model_call_trace_language_statement;
