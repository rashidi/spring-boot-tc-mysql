package scratches.tc.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import scratches.tc.configuration.DatasourceContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.http.HttpStatus.CREATED;

/**
 * @author Rashidi Zin
 */
@Testcontainers
@SpringBootTest(properties = "spring.jpa.generate-ddl=true", webEnvironment = RANDOM_PORT)
public class BookRepositoryRestResourceTests {

    @Autowired
    private TestRestTemplate restTemplate;

    @Container
    private static final DatasourceContainer datasource = new DatasourceContainer().withDatabaseName("demo");

    @DynamicPropertySource
    static void datasourceProperties(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", datasource::getJdbcUrl);
        registry.add("spring.datasource.username", datasource::getUsername);
        registry.add("spring.datasource.password", datasource::getPassword);
    }

    @Test
    @DisplayName("Entity will be created if datasource is available")
    void create() {
        var author = author();

        var book = book(author);

        ResponseEntity<Book> response = restTemplate.postForEntity("/books", book, Book.class);

        assertThat(response.getStatusCode()).isEqualTo(CREATED);
    }

    private Author author() {
        var author = new Author();

        author.setName("Rudyard Kipling");

        return author;
    }

    private Book book(final Author author) {
        var book = new Book();

        book.setAuthor(author);
        book.setTitle("The Jungle Book");

        return book;
    }

}
