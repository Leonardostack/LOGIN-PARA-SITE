-- Permite cadastrar estudantes antes de importar provas.
-- Execute uma vez em bancos existentes; nao altera estudantes ja cadastrados.
ALTER TABLE estudante MODIFY COLUMN id_prova INT DEFAULT NULL;
