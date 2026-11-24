package br.com.evoluamais.repository;

import br.com.evoluamais.config.ConexaoBanco;
import br.com.evoluamais.model.Estudante;
import br.com.evoluamais.model.Usuario;
import br.com.evoluamais.service.SenhaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;

@Repository
public class EstudanteRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(EstudanteRepository.class);

    @FunctionalInterface
    public interface Conexoes {
        Connection abrir() throws SQLException;
    }

    private final SenhaService senhaService;
    private final Conexoes conexoes;

    @Autowired
    public EstudanteRepository() {
        this(ConexaoBanco::conectar);
    }

    public EstudanteRepository(Conexoes conexoes) {
        this.conexoes = conexoes;
        this.senhaService = new SenhaService();
    }


    // LOGIN
    public Estudante fazerLogin(String email, String senhaDigitada) {

        String sql = """
                SELECT
                    e.id_estudante,
                    u.id_usuario,
                    u.nome,
                    u.email,
                    u.senha
                FROM estudante e
                INNER JOIN usuario u
                    ON e.id_usuario = u.id_usuario
                WHERE u.email = ?
                LIMIT 1
                """;

        try (Connection conexao = conexoes.abrir();
             PreparedStatement comando =
                     conexao.prepareStatement(sql)) {

            comando.setString(1, email);

            ResultSet resultado =
                    comando.executeQuery();

            if (resultado.next()) {

                int idUsuario =
                        resultado.getInt("id_usuario");

                String senhaBanco =
                        resultado.getString("senha");

                boolean senhaCorreta;

                // Senha já utiliza BCrypt
                if (senhaBanco != null &&
                        senhaBanco.startsWith("$2")) {

                    senhaCorreta =
                            senhaService.verificar(
                                    senhaDigitada,
                                    senhaBanco
                            );

                } else {

                    // Compatibilidade temporária
                    // com usuários antigos
                    senhaCorreta =
                            senhaBanco != null &&
                                    senhaBanco.equals(senhaDigitada);

                    // Se o login antigo estiver correto,
                    // converte automaticamente para BCrypt
                    if (senhaCorreta) {

                        atualizarSenhaParaHash(
                                idUsuario,
                                senhaDigitada
                        );
                    }
                }

                if (!senhaCorreta) {
                    return null;
                }

                Usuario usuario =
                        new Usuario();

                usuario.setIdUsuario(idUsuario);

                usuario.setNome(
                        resultado.getString("nome")
                );

                usuario.setEmail(
                        resultado.getString("email")
                );


                Estudante estudante =
                        new Estudante();

                estudante.setIdEstudante(
                        resultado.getInt(
                                "id_estudante"
                        )
                );

                estudante.setUsuario(usuario);

                return estudante;
            }

        } catch (SQLException e) {
            throw indisponivel("realizar login", e);
        }

        return null;
    }


    // CONVERTE SENHA ANTIGA PARA BCRYPT
    private void atualizarSenhaParaHash(
            int idUsuario,
            String senha) {

        String sql = """
                UPDATE usuario
                SET senha = ?
                WHERE id_usuario = ?
                """;

        String hash =
                senhaService.gerarHash(senha);

        try (Connection conexao =
                     conexoes.abrir();

             PreparedStatement comando =
                     conexao.prepareStatement(sql)) {

            comando.setString(1, hash);
            comando.setInt(2, idUsuario);

            comando.executeUpdate();

            System.out.println(
                    "Senha antiga convertida para BCrypt."
            );

        } catch (SQLException e) {
            LOGGER.error("Falha ao atualizar senha antiga. SQLState: {}", e.getSQLState());
        }
    }


    // VERIFICA SE O EMAIL JÁ EXISTE
    public boolean emailExiste(String email) {

        String sql = """
                SELECT id_usuario
                FROM usuario
                WHERE email = ?
                LIMIT 1
                """;

        try (Connection conexao =
                     conexoes.abrir();

             PreparedStatement comando =
                     conexao.prepareStatement(sql)) {

            comando.setString(1, email);

            ResultSet resultado =
                    comando.executeQuery();

            return resultado.next();

        } catch (SQLException e) {
            throw indisponivel("verificar o e-mail", e);
        }
    }


    // CADASTRO
    public Estudante cadastrar(
            String nome,
            String email,
            String senha,
            Integer idProva) {

        String sqlUsuario = """
                INSERT INTO usuario
                    (nome, email, senha)
                VALUES (?, ?, ?)
                """;

        String sqlEstudante = """
                INSERT INTO estudante
                    (id_usuario, id_prova)
                VALUES (?, ?)
                """;

        Connection conexao = null;

        try {

            conexao =
                    conexoes.abrir();

            conexao.setAutoCommit(false);

            int idUsuario;

            // Gera o hash ANTES de salvar
            String senhaHash =
                    senhaService.gerarHash(senha);


            try (PreparedStatement comandoUsuario =
                         conexao.prepareStatement(
                                 sqlUsuario,
                                 Statement.RETURN_GENERATED_KEYS
                         )) {

                comandoUsuario.setString(
                        1,
                        nome
                );

                comandoUsuario.setString(
                        2,
                        email
                );

                comandoUsuario.setString(
                        3,
                        senhaHash
                );

                comandoUsuario.executeUpdate();


                ResultSet chaves =
                        comandoUsuario.getGeneratedKeys();

                if (!chaves.next()) {

                    conexao.rollback();

                    return null;
                }

                idUsuario =
                        chaves.getInt(1);
            }


            int idEstudante;


            try (PreparedStatement comandoEstudante =
                         conexao.prepareStatement(
                                 sqlEstudante,
                                 Statement.RETURN_GENERATED_KEYS
                         )) {

                comandoEstudante.setInt(
                        1,
                        idUsuario
                );

                if (idProva == null) {
                    comandoEstudante.setNull(2, Types.INTEGER);
                } else {
                    comandoEstudante.setInt(2, idProva);
                }

                comandoEstudante.executeUpdate();


                ResultSet chaves =
                        comandoEstudante.getGeneratedKeys();

                if (!chaves.next()) {

                    conexao.rollback();

                    return null;
                }

                idEstudante =
                        chaves.getInt(1);
            }


            conexao.commit();


            Usuario usuario =
                    new Usuario();

            usuario.setIdUsuario(
                    idUsuario
            );

            usuario.setNome(
                    nome
            );

            usuario.setEmail(
                    email
            );


            Estudante estudante =
                    new Estudante();

            estudante.setIdEstudante(
                    idEstudante
            );

            estudante.setUsuario(
                    usuario
            );


            System.out.println(
                    "Estudante cadastrado com sucesso!"
            );

            return estudante;


        } catch (SQLException e) {

            if (conexao != null) {

                try {

                    conexao.rollback();

                } catch (SQLException erroRollback) {

                    erroRollback.printStackTrace();
                }
            }


            throw indisponivel("cadastrar estudante", e);

        } finally {

            if (conexao != null) {

                try {

                    conexao.setAutoCommit(true);
                    conexao.close();

                } catch (SQLException e) {
                    LOGGER.error("Falha ao encerrar conexao. SQLState: {}", e.getSQLState());
                }
            }
        }
    }

    private ResponseStatusException indisponivel(String operacao, SQLException erro) {
        LOGGER.error("Falha ao {}. SQLState: {}", operacao, erro.getSQLState());
        return new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "O banco de dados está indisponível. Tente novamente mais tarde."
        );
    }
}
