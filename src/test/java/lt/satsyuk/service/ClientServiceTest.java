package lt.satsyuk.service;

import lt.satsyuk.dto.ClientResponse;
import lt.satsyuk.dto.CreateClientRequest;
import lt.satsyuk.exception.ClientSearchQueryTooShortException;
import lt.satsyuk.exception.PhoneAlreadyExistsException;
import lt.satsyuk.mapper.ClientMapper;
import lt.satsyuk.model.Account;
import lt.satsyuk.model.Client;
import lt.satsyuk.repository.AccountRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientServiceTest {

    private static final int SEARCH_MAX_RESULTS = 2;

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private ClientMapper clientMapper;

    private ClientService clientService;

    @BeforeEach
    void setUp() {
        clientService = new ClientService(clientRepository, accountRepository, clientMapper, SEARCH_MAX_RESULTS);
    }

    @Test
    void createThrowsWhenPhoneAlreadyExists() {
        CreateClientRequest request = new CreateClientRequest(null, "John", "Doe", "+37060000001");
        when(clientRepository.existsByPhone(request.phone())).thenReturn(true);

        assertThatThrownBy(() -> clientService.create(request))
                .isInstanceOf(PhoneAlreadyExistsException.class)
                .hasMessageContaining(request.phone());
    }

    @Test
    void createSavesClientAndCreatesZeroBalanceAccount() {
        CreateClientRequest request = new CreateClientRequest(null, "John", "Doe", "+37060000001");
        Client entity = Client.builder().firstName("John").lastName("Doe").phone("+37060000001").build();
        Client saved = Client.builder().id(7L).firstName("John").lastName("Doe").phone("+37060000001").build();
        ClientResponse response = new ClientResponse(7L, "John", "Doe", "+37060000001");

        when(clientRepository.existsByPhone(request.phone())).thenReturn(false);
        when(clientMapper.toEntity(request)).thenReturn(entity);
        when(clientRepository.saveAndFlush(entity)).thenReturn(saved);
        when(clientMapper.toResponse(saved)).thenReturn(response);

        ClientResponse actual = clientService.create(request);

        assertThat(actual).isEqualTo(response);
        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).saveAndFlush(accountCaptor.capture());
        Account createdAccount = accountCaptor.getValue();
        assertThat(createdAccount.getClient()).isEqualTo(saved);
        assertThat(createdAccount.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void createMapsClientSaveConstraintViolationToPhoneAlreadyExistsException() {
        CreateClientRequest request = new CreateClientRequest(null, "John", "Doe", "+37060000001");
        Client entity = Client.builder().firstName("John").lastName("Doe").phone("+37060000001").build();

        when(clientRepository.existsByPhone(request.phone())).thenReturn(false);
        when(clientMapper.toEntity(request)).thenReturn(entity);
        when(clientRepository.saveAndFlush(entity)).thenThrow(new DataIntegrityViolationException("duplicate phone"));

        assertThatThrownBy(() -> clientService.create(request))
                .isInstanceOf(PhoneAlreadyExistsException.class)
                .hasMessageContaining(request.phone());

        verify(accountRepository, never()).saveAndFlush(any(Account.class));
    }

    @Test
    void createDoesNotMapAccountSaveFailureToPhoneAlreadyExistsException() {
        CreateClientRequest request = new CreateClientRequest(null, "John", "Doe", "+37060000001");
        Client entity = Client.builder().firstName("John").lastName("Doe").phone("+37060000001").build();
        Client saved = Client.builder().id(7L).firstName("John").lastName("Doe").phone("+37060000001").build();

        when(clientRepository.existsByPhone(request.phone())).thenReturn(false);
        when(clientMapper.toEntity(request)).thenReturn(entity);
        when(clientRepository.saveAndFlush(entity)).thenReturn(saved);
        when(accountRepository.saveAndFlush(any(Account.class))).thenThrow(new DataIntegrityViolationException("account failure"));

        assertThatThrownBy(() -> clientService.create(request))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("account failure");
    }

    @Test
    void searchByNameOrSurnameThrowsWhenQueryTooShort() {
        assertThatThrownBy(() -> clientService.searchByNameOrSurname("ab"))
                .isInstanceOfSatisfying(ClientSearchQueryTooShortException.class,
                        ex -> assertThat(ex.getMinLength()).isEqualTo(ClientService.MIN_SEARCH_QUERY_LENGTH));
    }

    @Test
    void searchByNameOrSurnameReturnsMappedResults() {
        Client first = Client.builder().id(1L).firstName("Alice").lastName("Smith").phone("+37060000001").build();
        Client second = Client.builder().id(2L).firstName("Alina").lastName("Johnson").phone("+37060000002").build();
        ClientResponse firstResponse = new ClientResponse(1L, "Alice", "Smith", "+37060000001");
        ClientResponse secondResponse = new ClientResponse(2L, "Alina", "Johnson", "+37060000002");

        when(clientRepository.findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCaseOrderByIdAsc(
                "Ali",
                "Ali",
                PageRequest.of(0, SEARCH_MAX_RESULTS)
        ))
                .thenReturn(List.of(first, second));
        when(clientMapper.toResponse(first)).thenReturn(firstResponse);
        when(clientMapper.toResponse(second)).thenReturn(secondResponse);

        List<ClientResponse> actual = clientService.searchByNameOrSurname("  Ali  ");

        assertThat(actual).containsExactly(firstResponse, secondResponse);
    }
}
