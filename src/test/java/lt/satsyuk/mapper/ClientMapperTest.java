package lt.satsyuk.mapper;

import lt.satsyuk.dto.ClientResponse;
import lt.satsyuk.dto.CreateClientRequest;
import lt.satsyuk.model.Client;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClientMapperTest {

    private final ClientMapper mapper = ClientMapper.INSTANCE;

    @Test
    void toEntityMapsFieldsAndIgnoresId() {
        CreateClientRequest request = new CreateClientRequest(
                null,
                "John",
                "Doe",
                "+12345678901"
        );

        Client entity = mapper.toEntity(request);

        assertThat(entity)
                .extracting(Client::getFirstName, Client::getLastName, Client::getPhone)
                .containsExactly("John", "Doe", "+12345678901");
    }

    @Test
    void toResponseMapsAllFields() {
        Client client = Client.builder()
                .id(42L)
                .firstName("Jane")
                .lastName("Roe")
                .phone("+37060000000")
                .build();

        ClientResponse response = mapper.toResponse(client);

        assertThat(response)
                .extracting(ClientResponse::id,
                        ClientResponse::firstName,
                        ClientResponse::lastName,
                        ClientResponse::phone)
                        .containsExactly(42L, "Jane", "Roe", "+37060000000");
    }
}

