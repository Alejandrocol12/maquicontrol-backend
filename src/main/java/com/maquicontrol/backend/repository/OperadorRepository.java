package com.maquicontrol.backend.repository;

import com.maquicontrol.backend.model.Operador;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OperadorRepository extends JpaRepository<Operador, Long> {
    List<Operador> findByUsuarioId(Long usuarioId);
    List<Operador> findByTelefono(String telefono);
    java.util.Optional<Operador> findByTelegramChatId(Long chatId);
    java.util.Optional<Operador> findByTelegramLinkCode(String code);
}
