package com.casms.testng;

import static org.mockito.Mockito.*;

import com.casms.common.exception.BusinessException;
import com.casms.identity.enums.Role;
import com.casms.identity.repository.UserRepository;
import com.casms.identity.service.EmployeeAccountService;
import com.casms.security.principal.CasmsPrincipal;
import com.casms.testng.listener.CasmsTestListener;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testng.Assert;
import org.testng.annotations.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Listeners(CasmsTestListener.class)
public class EmployeeAccountTestNG {

    private AutoCloseable mocks;
    private ValidatorFactory validatorFactory;
    private Validator validator;

    @Mock private UserRepository users;
    @Mock private JdbcTemplate jdbc;

    @InjectMocks private EmployeeAccountService service;

    @BeforeMethod(alwaysRun = true)
    public void setUp() {
        service = null;
        mocks = MockitoAnnotations.openMocks(this);
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() throws Exception {
        if (validatorFactory != null) {
            validatorFactory.close();
        }
        if (mocks != null) {
            mocks.close();
        }
    }

    @DataProvider(name = "employeeCodeBoundaryData")
    public Object[][] employeeCodeBoundaryData() {
        return new Object[][] {
            {"EMP-001", true},
            {"OM_CASMS.01", true},
            {"A", true},
            {"A" + "1".repeat(49), true},
            {"A" + "1".repeat(50), false},
            {"Ngô Gia Thành", false},
            {"EMP 001", false},
            {"-LEADING-DASH", false},
            {"", false},
            {"   ", false},
        };
    }

    @Test(dataProvider = "employeeCodeBoundaryData", groups = {"smoke", "regression", "boundary"})
    public void testEmployeeCodeRegexAndLengthValidation(String code, boolean expectedValid) {
        EmployeeAccountService.CreateInput input =
                new EmployeeAccountService.CreateInput(
                        code,
                        "Nguyen Van Test",
                        "employee@casms.com",
                        "0912345678",
                        "Male",
                        LocalDate.of(1995, 5, 10),
                        "Ha Noi",
                        "Operations",
                        "Staff",
                        Role.ONBOARD_STAFF);

        Set<ConstraintViolation<EmployeeAccountService.CreateInput>> violations =
                validator.validate(input);

        Assert.assertEquals(
                violations.isEmpty(),
                expectedValid,
                "Unexpected validation outcome for employeeCode='" + code + "': " + violations);
    }

    @Test(groups = {"smoke", "regression", "security"})
    public void testAdminAssignableRolesIncludeAllSystemRoles() {
        CasmsPrincipal admin = mockActor(true);
        List<Role> roles = service.assignableRoles(admin);
        Assert.assertEquals(roles, List.of(Role.values()));
    }

    @DataProvider(name = "hrForbiddenRolesData")
    public Object[][] hrForbiddenRolesData() {
        return new Object[][] {
            {Role.ADMIN},
            {Role.HR_MANAGER},
            {Role.CRUISE_MANAGER},
            {Role.OPERATIONS_MANAGER},
        };
    }

    @Test(dataProvider = "hrForbiddenRolesData", groups = {"regression", "security"})
    public void testHrManagerCannotProvisionPrivilegedRole(Role forbiddenRole) {
        CasmsPrincipal hr = mockActor(false);
        EmployeeAccountService.CreateInput input =
                new EmployeeAccountService.CreateInput(
                        "EMP-099",
                        "Staff Member",
                        "staff@casms.com",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        forbiddenRole);

        BusinessException ex =
                Assert.expectThrows(BusinessException.class, () -> service.create(input, hr));
        Assert.assertEquals(ex.getCode(), "EMPLOYEE_ROLE_FORBIDDEN");
        verifyNoInteractions(jdbc, users);
    }

    private CasmsPrincipal mockActor(boolean isAdmin) {
        CasmsPrincipal actor = mock(CasmsPrincipal.class);
        when(actor.isEnabled()).thenReturn(true);
        when(actor.isAccountNonLocked()).thenReturn(true);
        when(actor.hasAnyRole(Role.ADMIN, Role.HR_MANAGER)).thenReturn(true);
        when(actor.hasAuthority("USER_CREATE")).thenReturn(true);
        when(actor.hasRole(Role.ADMIN)).thenReturn(isAdmin);
        return actor;
    }
}
