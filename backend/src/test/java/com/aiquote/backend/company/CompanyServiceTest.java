package com.aiquote.backend.company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiquote.backend.file.StorageService;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Covers Etap 17's branding rules: logo upload validation (size/MIME), replacing a
 * logo deletes the old MinIO object, defaults apply when nothing is customized, and
 * every operation only ever touches the caller's own company (see CompanyController's
 * javadoc — there's no request parameter through which a company ID could be spoofed,
 * so these tests document that rather than probing for a bypass that structurally
 * cannot exist).
 */
@ExtendWith(MockitoExtension.class)
class CompanyServiceTest {

    /** Real PNG signature bytes (Etap 21 added magic-byte validation — a MockMultipartFile
     * declaring image/png must actually start with these to pass uploadLogo). */
    private static final byte[] PNG_BYTES = new byte[] {
        (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private StorageService storageService;

    private CompanyService service;

    @BeforeEach
    void setUp() {
        service = new CompanyService(companyRepository, storageService);
    }

    @Test
    void uploadLogoStoresFileAndAttachesItToCompany() {
        Company company = company(5L);
        when(companyRepository.findById(5L)).thenReturn(Optional.of(company));
        when(storageService.store(eq(5L), anyString(), eq("image/png"), any(InputStream.class), anyLong())).thenReturn("logos/5-abc.png");
        MockMultipartFile file = new MockMultipartFile("file", "logo.png", "image/png", PNG_BYTES);

        Company updated = service.uploadLogo(5L, file);

        assertThat(updated.getLogoStorageKey()).isEqualTo("logos/5-abc.png");
        assertThat(updated.getLogoContentType()).isEqualTo("image/png");
        assertThat(updated.hasLogo()).isTrue();
    }

    @Test
    void uploadingANewLogoDeletesThePreviousOne() {
        Company company = company(5L);
        ReflectionTestUtils.setField(company, "logoStorageKey", "logos/old.png");
        ReflectionTestUtils.setField(company, "logoContentType", "image/png");
        when(companyRepository.findById(5L)).thenReturn(Optional.of(company));
        when(storageService.store(eq(5L), anyString(), anyString(), any(InputStream.class), anyLong())).thenReturn("logos/new.png");
        MockMultipartFile file = new MockMultipartFile("file", "logo.png", "image/png", PNG_BYTES);

        service.uploadLogo(5L, file);

        verify(storageService).delete("logos/old.png");
    }

    @Test
    void rejectsAnEmptyFile() {
        MockMultipartFile file = new MockMultipartFile("file", "logo.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> service.uploadLogo(5L, file)).isInstanceOf(InvalidLogoException.class);
        verify(storageService, never()).store(any(), any(), any(), any(), anyLong());
    }

    @Test
    void rejectsAFileOverTheSizeLimit() {
        MockMultipartFile file = new MockMultipartFile("file", "logo.png", "image/png", new byte[3 * 1024 * 1024]);

        assertThatThrownBy(() -> service.uploadLogo(5L, file)).isInstanceOf(InvalidLogoException.class);
    }

    @Test
    void rejectsAnUnsupportedContentType() {
        MockMultipartFile file = new MockMultipartFile("file", "logo.svg", "image/svg+xml", new byte[] {1, 2, 3});

        assertThatThrownBy(() -> service.uploadLogo(5L, file)).isInstanceOf(InvalidLogoException.class);
    }

    /** Etap 21: a client can declare any Content-Type it wants — the actual bytes must
     * match, not just the header the client claims. */
    @Test
    void rejectsAFileWhoseBytesDontMatchItsDeclaredImageContentType() {
        MockMultipartFile file = new MockMultipartFile("file", "logo.png", "image/png", "not actually a png".getBytes());

        assertThatThrownBy(() -> service.uploadLogo(5L, file)).isInstanceOf(InvalidLogoException.class);
        verify(storageService, never()).store(any(), any(), any(), any(), anyLong());
    }

    @Test
    void removeLogoClearsTheEntityAndDeletesTheStoredFile() {
        Company company = company(5L);
        ReflectionTestUtils.setField(company, "logoStorageKey", "logos/old.png");
        ReflectionTestUtils.setField(company, "logoContentType", "image/png");
        when(companyRepository.findById(5L)).thenReturn(Optional.of(company));

        service.removeLogo(5L);

        assertThat(company.hasLogo()).isFalse();
        verify(storageService).delete("logos/old.png");
    }

    @Test
    void removingALogoThatDoesNotExistIsANoOp() {
        Company company = company(5L);
        when(companyRepository.findById(5L)).thenReturn(Optional.of(company));

        service.removeLogo(5L);

        verify(storageService, never()).delete(any());
    }

    @Test
    void getLogoReturnsStoredBytesAndContentType() {
        Company company = company(5L);
        ReflectionTestUtils.setField(company, "logoStorageKey", "logos/old.png");
        ReflectionTestUtils.setField(company, "logoContentType", "image/webp");
        when(companyRepository.findById(5L)).thenReturn(Optional.of(company));
        when(storageService.retrieve("logos/old.png")).thenReturn(new ByteArrayInputStream(new byte[] {9, 9}));

        LogoContent logo = service.getLogo(5L);

        assertThat(logo.bytes()).containsExactly(9, 9);
        assertThat(logo.contentType()).isEqualTo("image/webp");
    }

    @Test
    void getLogoThrowsWhenCompanyHasNoLogo() {
        Company company = company(5L);
        when(companyRepository.findById(5L)).thenReturn(Optional.of(company));

        assertThatThrownBy(() -> service.getLogo(5L)).isInstanceOf(LogoNotFoundException.class);
    }

    @Test
    void updateBrandingSetsAllFieldsAndBlankStringsBecomeNull() {
        Company company = company(5L);
        when(companyRepository.findById(5L)).thenReturn(Optional.of(company));

        Company updated = service.updateBranding(5L, new UpdateBrandingRequest("Marka", "#112233", "Witaj!", "kontakt@firma.pl", "  ", ""));

        assertThat(updated.getDisplayName()).isEqualTo("Marka");
        assertThat(updated.getResolvedPrimaryColor()).isEqualTo("#112233");
        assertThat(updated.getResolvedWelcomeText()).isEqualTo("Witaj!");
        assertThat(updated.getContactEmail()).isEqualTo("kontakt@firma.pl");
        assertThat(updated.getContactPhone()).isNull();
        assertThat(updated.getAddress()).isNull();
    }

    @Test
    void unsetBrandingFieldsResolveToDefaults() {
        Company company = company(5L);

        assertThat(company.getDisplayName()).isEqualTo("Testowa Firma");
        assertThat(company.getResolvedPrimaryColor()).isEqualTo(Company.DEFAULT_PRIMARY_COLOR);
        assertThat(company.getResolvedWelcomeText()).isEqualTo(Company.DEFAULT_WELCOME_TEXT);
        assertThat(company.hasLogo()).isFalse();
    }

    @Test
    void companyOperationsOnlyEverReachTheGivenCompanyId() {
        Company companyA = company(5L);
        Company companyB = company(6L);
        ReflectionTestUtils.setField(companyB, "displayName", "Firma B");
        when(companyRepository.findById(5L)).thenReturn(Optional.of(companyA));

        Company result = service.updateBranding(5L, new UpdateBrandingRequest("Firma A", null, null, null, null, null));

        assertThat(result.getId()).isEqualTo(5L);
        assertThat(result.getDisplayName()).isEqualTo("Firma A");
        assertThat(companyB.getDisplayName()).isEqualTo("Firma B");
    }

    private Company company(long id) {
        Company company = new Company("Testowa Firma", "testowa-firma", CompanyStatus.ACTIVE);
        ReflectionTestUtils.setField(company, "id", id);
        return company;
    }
}
