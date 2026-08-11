-- Paragraph-only bindings predate text-range anchors and cannot be reconstructed
-- from the stored paragraph id. Remove them instead of rendering a misleading
-- paragraph-level attachment marker.
UPDATE diary
SET attachment_bindings = '[]'
WHERE attachment_bindings IS NOT NULL
  AND JSON_VALID(attachment_bindings)
  AND TRIM(attachment_bindings) <> '[]'
  AND attachment_bindings NOT LIKE '%"anchor"%';
