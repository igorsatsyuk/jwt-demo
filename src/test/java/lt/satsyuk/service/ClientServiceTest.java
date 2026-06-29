package lt.satsyuk.service;

import lt.satsyuk.dto.ClientResponse;
import lt.satsyuk.dto.CreateClientRequest;
import lt.satsyuk.exception.ClientSearchQueryTooShortException;
import lt.satsyuk.mapper.ClientMapper;
import lt.satsyuk.model.Account;
import lt.satsyuk.model.Client;
import lt.satsyuk.model.ClientAccess;
import lt.satsyuk.repository.AccountRepository;
import lt.satsyuk.repository.ClientAccessRepository;
import lt.satsyuk.repository.ClientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientServiceTest {

    private static final int SEARCH_MAX_RESULTS = 2;
    private static final String AUTH_CLIENT_ID = "spring-app";

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private ClientAccessRepository clientAccessRepository;

    @Mock
    private ClientMapper clientMapper;

    private ClientService clientService;

    @BeforeEach
    void setUp() {
        clientService = new ClientService(clientRepository, accountRepository, clientAccessRepository, clientMapper, SEARCH_MAX_RESULTS);
    }

    @Test
    void createReturnsExistingClientAndAddsAccessWhenPhoneExists() {
        CreateClientRequest request = new CreateClientRequest(null, "John", "Doe", "+37060000001");
        Client existing = Client.builder().id(7L).firstName("John").lastName("Doe").phone("+37060000001").build();
        ClientResponse response = new ClientResponse(7L, "John", "Doe", "+37060000001");

        when(clientRepository.findByPhone(request.phone())).thenReturn(Optional.of(existing));
        when(clientAccessRepository.existsByClientIdAndAuthClientId(7L, AUTH_CLIENT_ID)).thenReturn(false);
        when(clientMapper.toResponse(existing)).thenReturn(response);

        ClientResponse actual = clientService.create(request, AUTH_CLIENT_ID);

        assertThat(actual).isEqualTo(response);
        verify(clientAccessRepository).save(any(ClientAccess.class));
        verify(accountRepository, never()).saveAndFlush(any(Account.class));
    }

    @Test
    void createDoesNotDuplicateAccessWhenPhoneExistsAndAccessAlreadyPresent() {
        CreateClientRequest request = new CreateClientRequest(null, "John", "Doe", "+37060000001");
        Client existing = Client.builder().id(7L).firstName("John").lastName("Doe").phone("+37060000001").build();
        ClientResponse response = new ClientResponse(7L, "John", "Doe", "+37060000001");

        when(clientRepository.findByPhone(request.phone())).thenReturn(Optional.of(existing));
        when(clientAccessRepository.existsByClientIdAndAuthClientId(7L, AUTH_CLIENT_ID)).thenReturn(true);
        when(clientMapper.toResponse(existing)).thenReturn(response);

        ClientResponse actual = clientService.create(request, AUTH_CLIENT_ID);

        assertThat(actual).isEqualTo(response);
        verify(clientAccessRepository, never()).save(any(ClientAccess.class));
    }

    @Test
    void createSavesClientAndCreatesZeroBalanceAccount() {
        CreateClientRequest request = new CreateClientRequest(null, "John", "Doe", "+37060000001");
        Client entity = Client.builder().firstName("John").lastName("Doe").phone("+37060000001").build();
        Client saved = Client.builder().id(7L).firstName("John").lastName("Doe").phone("+37060000001").build();
        ClientResponse response = new ClientResponse(7L, "John", "Doe", "+37060000001");

        when(clientRepository.findByPhone(request.phone())).thenReturn(Optional.empty());
        when(clientMapper.toEntity(request)).thenReturn(entity);
        when(clientRepository.saveAndFlush(entity)).thenReturn(saved);
        when(clientMapper.toResponse(saved)).thenReturn(response);

        ClientResponse actual = clientService.create(request, AUTH_CLIENT_ID);

        assertThat(actual).isEqualTo(response);
        verify(clientAccessRepository).save(any(ClientAccess.class));

        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).saveAndFlush(accountCaptor.capture());
        Account createdAccount = accountCaptor.getValue();
        assertThat(createdAccount.getClient()).isEqualTo(saved);
        assertThat(createdAccount.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void createMapsClientSaveConstraintViolationToExistingClient() {
        CreateClientRequest request = new CreateClientRequest(null, "John", "Doe", "+37060000001");
        Client entity = Client.builder().firstName("John").lastName("Doe").phone("+37060000001").build();
        Client existing = Client.builder().id(7L).firstName("John").lastName("Doe").phone("+37060000001").build();
        ClientResponse response = new ClientResponse(7L, "John", "Doe", "+37060000001");

        when(clientRepository.findByPhone(request.phone()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));
        when(clientMapper.toEntity(request)).thenReturn(entity);
        when(clientRepository.saveAndFlush(entity)).thenThrow(new DataIntegrityViolationException("duplicate phone"));
        when(clientAccessRepository.existsByClientIdAndAuthClientId(7L, AUTH_CLIENT_ID)).thenReturn(false);
        when(clientMapper.toResponse(existing)).thenReturn(response);

        ClientResponse actual = clientService.create(request, AUTH_CLIENT_ID);

        assertThat(actual).isEqualTo(response);
        verify(clientAccessRepository).save(any(ClientAccess.class));
    }

    @Test
    void searchByNameOrSurnameThrowsWhenQueryTooShort() {
        assertThatThrownBy(() -> clientService.searchByNameOrSurname("ab", AUTH_CLIENT_ID))
                .isInstanceOfSatisfying(ClientSearchQueryTooShortException.class,
                        ex -> assertThat(ex.getMinLength()).isEqualTo(ClientService.MIN_SEARCH_QUERY_LENGTH));
    }

    @Test
    void searchByNameOrSurnameReturnsMappedResults() {
        Client first = Client.builder().id(1L).firstName("Alice").lastName("Smith").phone("+37060000001").build();
        Client second = Client.builder().id(2L).firstName("Alina").lastName("Johnson").phone("+37060000002").build();
        ClientResponse firstResponse = new ClientResponse(1L, "Alice", "Smith", "+37060000001");
        ClientResponse secondResponse = new ClientResponse(2L, "Alina", "Johnson", "+37060000002");

        when(clientRepository.searchByNameOrSurnameAndAuthClientId(
                "Ali",
                AUTH_CLIENT_ID,
                PageRequest.of(0, SEARCH_MAX_RESULTS)
        ))
                .thenReturn(List.of(first, second));
        when(clientMapper.toResponse(first)).thenReturn(firstResponse);
        when(clientMapper.toResponse(second)).thenReturn(secondResponse);

        List<ClientResponse> actual = clientService.searchByNameOrSurname("  Ali  ", AUTH_CLIENT_ID);

        assertThat(actual).containsExactly(firstResponse, secondResponse);
    }
}
