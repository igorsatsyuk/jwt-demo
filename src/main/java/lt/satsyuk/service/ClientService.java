package lt.satsyuk.service;

import lt.satsyuk.dto.ClientResponse;
import lt.satsyuk.dto.CreateClientRequest;
import lt.satsyuk.exception.ClientSearchQueryTooShortException;
import lt.satsyuk.exception.ClientNotFoundException;
import lt.satsyuk.exception.PhoneAlreadyExistsException;
import lt.satsyuk.mapper.ClientMapper;
import lt.satsyuk.model.Account;
import lt.satsyuk.model.Client;
import lt.satsyuk.model.ClientAccess;
import lt.satsyuk.repository.AccountRepository;
import lt.satsyuk.repository.ClientAccessRepository;
import lt.satsyuk.repository.ClientRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class ClientService {

    public static final int MIN_SEARCH_QUERY_LENGTH = 3;

    private final ClientRepository repo;
    private final AccountRepository accountRepository;
    private final ClientAccessRepository clientAccessRepository;
    private final ClientMapper mapper;
    private final int searchMaxResults;

    public ClientService(ClientRepository repo,
                         AccountRepository accountRepository,
                         ClientAccessRepository clientAccessRepository,
                         ClientMapper mapper,
                         @Value("${app.clients.search.max-results:20}") int searchMaxResults) {
        this.repo = repo;
        this.accountRepository = accountRepository;
        this.clientAccessRepository = clientAccessRepository;
        this.mapper = mapper;
        this.searchMaxResults = Math.max(1, searchMaxResults);
    }

    @Transactional
    public ClientResponse create(CreateClientRequest req, String authClientId) {

        if (repo.existsByPhone(req.phone())) {
            throw new PhoneAlreadyExistsException(req.phone());
        }

        Client saved;
        try {
            Client client = mapper.toEntity(req);
            saved = repo.saveAndFlush(client);
        } catch (DataIntegrityViolationException _) {
            throw new PhoneAlreadyExistsException(req.phone());
        }

        clientAccessRepository.save(ClientAccess.builder()
                .clientId(saved.getId())
                .authClientId(authClientId)
                .build());

        accountRepository.saveAndFlush(Account.builder()
                .client(saved)
                .balance(BigDecimal.ZERO)
                .build());

        return mapper.toResponse(saved);
    }

    public ClientResponse get(Long id, String authClientId) {
        Client client = repo.findByIdAndAuthClientId(id, authClientId)
                .orElseThrow(() -> new ClientNotFoundException(id));

        return mapper.toResponse(client);
    }

    public List<ClientResponse> searchByNameOrSurname(String query, String authClientId) {
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.length() < MIN_SEARCH_QUERY_LENGTH) {
            throw new ClientSearchQueryTooShortException(MIN_SEARCH_QUERY_LENGTH);
        }

        return repo.searchByNameOrSurnameAndAuthClientId(
                        normalizedQuery,
                        authClientId,
                        PageRequest.of(0, searchMaxResults)
                ).stream()
                .map(mapper::toResponse)
                .toList();
    }
}
