package br.com.evoluamais.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.sql.DriverManager;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EstudanteRepositoryTest {
    private String url;
    private EstudanteRepository repository;
    private MockMvc mvc;

    @BeforeEach
    void preparar() throws Exception {
        url = "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
        try (var conexao = DriverManager.getConnection(url); var comando = conexao.createStatement()) {
            comando.execute("""
                    CREATE TABLE usuario (
                        id_usuario INT AUTO_INCREMENT PRIMARY KEY,
                        nome VARCHAR(100) NOT NULL,
                        email VARCHAR(100) NOT NULL,
                        senha VARCHAR(255) NOT NULL
                    )
                    """);
            comando.execute("""
                    CREATE TABLE estudante (
                        id_estudante INT AUTO_INCREMENT PRIMARY KEY,
                        id_usuario INT NOT NULL,
                        id_prova INT NULL
                    )
                    """);
        }
        repository = new EstudanteRepository(() -> DriverManager.getConnection(url));
        mvc = MockMvcBuilders.standaloneSetup(
                new br.com.evoluamais.controller.CadastroController(repository),
                new br.com.evoluamais.controller.LoginController(repository)
        ).build();
    }

    @Test
    void cadastraEPermiteEntrarSemProvasImportadas() throws Exception {
        MvcResult cadastro = mvc.perform(post("/cadastro")
                        .param("nome", "Maria Souza")
                        .param("email", "maria@example.com")
                        .param("senha", "senha-segura"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sucesso").value(true))
                .andExpect(request().sessionAttribute("nomeEstudante", "Maria Souza"))
                .andReturn();

        MockHttpSession sessao = (MockHttpSession) cadastro.getRequest().getSession(false);
        mvc.perform(post("/login")
                        .session(sessao)
                        .param("email", "maria@example.com")
                        .param("senha", "senha-segura"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sucesso").value(true))
                .andExpect(jsonPath("$.email").value("maria@example.com"));

        try (var conexao = DriverManager.getConnection(url);
             var comando = conexao.prepareStatement("""
                     SELECT u.senha, e.id_prova
                     FROM usuario u JOIN estudante e ON e.id_usuario = u.id_usuario
                     WHERE u.email = ?
                     """)) {
            comando.setString(1, "maria@example.com");
            try (var resultado = comando.executeQuery()) {
                assertTrue(resultado.next());
                assertTrue(resultado.getString("senha").startsWith("$2"));
                resultado.getInt("id_prova");
                assertTrue(resultado.wasNull());
            }
        }

        assertTrue(repository.emailExiste("maria@example.com"));
    }

    @Test
    void rejeitaSenhaIncorreta() throws Exception {
        repository.cadastrar("Maria Souza", "maria@example.com", "senha-segura", null);

        mvc.perform(post("/login")
                        .param("email", "maria@example.com")
                        .param("senha", "incorreta"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sucesso").value(false));
    }

    @Test
    void informaBancoIndisponivelNoCadastroENoLogin() throws Exception {
        var indisponivel = new EstudanteRepository(() -> {
            throw new java.sql.SQLException("database offline");
        });
        MockMvc api = MockMvcBuilders.standaloneSetup(
                new br.com.evoluamais.controller.CadastroController(indisponivel),
                new br.com.evoluamais.controller.LoginController(indisponivel)
        ).build();

        api.perform(post("/cadastro")
                        .param("nome", "Maria Souza")
                        .param("email", "maria@example.com")
                        .param("senha", "senha-segura"))
                .andExpect(status().isServiceUnavailable());
        api.perform(post("/login")
                        .param("email", "maria@example.com")
                        .param("senha", "senha-segura"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void direcionaRaizParaTelaDeLogin() throws Exception {
        MockMvc site = MockMvcBuilders.standaloneSetup(
                new br.com.evoluamais.controller.HomeController()
        ).build();

        site.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl("/log.html"));
    }
}
