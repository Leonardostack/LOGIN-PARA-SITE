package br.com.evoluamais.controller;

import br.com.evoluamais.model.Estudante;
import br.com.evoluamais.repository.EstudanteRepository;

import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/cadastro")
public class CadastroController {

    private final EstudanteRepository estudanteRepository;

    public CadastroController(EstudanteRepository estudanteRepository) {
        this.estudanteRepository = estudanteRepository;
    }

    @PostMapping
    public Map<String, Object> cadastrar(
            @RequestParam String nome,
            @RequestParam String email,
            @RequestParam String senha,
            HttpServletRequest request) {

        Map<String, Object> resposta = new HashMap<>();

        // Verifica se o e-mail já está cadastrado
        if (estudanteRepository.emailExiste(email)) {

            resposta.put("sucesso", false);
            resposta.put(
                    "mensagem",
                    "Este e-mail já está cadastrado."
            );

            return resposta;
        }

        Estudante estudante =
                estudanteRepository.cadastrar(
                        nome,
                        email,
                        senha,
                        null
                );

        if (estudante == null) {

            resposta.put("sucesso", false);
            resposta.put(
                    "mensagem",
                    "Não foi possível realizar o cadastro."
            );

            return resposta;
        }

        var session = request.getSession();
        request.changeSessionId();
        session.setAttribute("idEstudante", estudante.getIdEstudante());
        session.setAttribute("nomeEstudante", estudante.getUsuario().getNome());
        resposta.put("sucesso", true);
        resposta.put(
                "mensagem",
                "Cadastro realizado com sucesso!"
        );

        resposta.put(
                "idEstudante",
                estudante.getIdEstudante()
        );

        resposta.put(
                "nome",
                estudante.getUsuario().getNome()
        );

        return resposta;
    }
}
