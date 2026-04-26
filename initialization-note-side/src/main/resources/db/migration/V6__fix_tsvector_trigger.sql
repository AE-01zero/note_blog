-- V6: 修复 tsvector 触发器，t_file_info 无 content 字段，改用 original_filename
CREATE OR REPLACE FUNCTION update_file_tsvector()
    RETURNS trigger AS $$
BEGIN
    NEW.content_tsv :=
        to_tsvector('simple', coalesce(NEW.original_filename, ''));
    RETURN NEW;
END
$$ LANGUAGE plpgsql;
