package org.sagebionetworks.bridge.sdk.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.sagebionetworks.bridge.rest.model.EnrollmentFilter.ENROLLED;
import static org.sagebionetworks.bridge.rest.model.EnrollmentFilter.WITHDRAWN;
import static org.sagebionetworks.bridge.rest.model.Role.RESEARCHER;
import static org.sagebionetworks.bridge.rest.model.Role.STUDY_COORDINATOR;
import static org.sagebionetworks.bridge.rest.model.Role.WORKER;
import static org.sagebionetworks.bridge.sdk.integration.Tests.ORG_ID_1;
import static org.sagebionetworks.bridge.sdk.integration.Tests.ORG_ID_2;
import static org.sagebionetworks.bridge.sdk.integration.Tests.STUDY_ID_1;
import static org.sagebionetworks.bridge.sdk.integration.Tests.STUDY_ID_2;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import org.sagebionetworks.bridge.rest.api.ForAdminsApi;
import org.sagebionetworks.bridge.rest.api.ForResearchersApi;
import org.sagebionetworks.bridge.rest.api.ForWorkersApi;
import org.sagebionetworks.bridge.rest.api.OrganizationsApi;
import org.sagebionetworks.bridge.rest.api.ParticipantsApi;
import org.sagebionetworks.bridge.rest.api.StudiesApi;
import org.sagebionetworks.bridge.rest.api.StudyParticipantsApi;
import org.sagebionetworks.bridge.rest.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.rest.model.AccountStatus;
import org.sagebionetworks.bridge.rest.model.AccountSummary;
import org.sagebionetworks.bridge.rest.model.AccountSummaryList;
import org.sagebionetworks.bridge.rest.model.AccountSummarySearch;
import org.sagebionetworks.bridge.rest.model.Enrollment;
import org.sagebionetworks.bridge.rest.model.RequestParams;
import org.sagebionetworks.bridge.rest.model.Role;
import org.sagebionetworks.bridge.rest.model.SignUp;
import org.sagebionetworks.bridge.rest.model.StudyParticipant;
import org.sagebionetworks.bridge.user.TestUserHelper;
import org.sagebionetworks.bridge.user.TestUserHelper.TestUser;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class AccountSummarySearchTest {

    private static final ArrayList<String> TEST_USER_GROUPS = Lists.newArrayList("test_user", "sdk-int-1");
    private static final ArrayList<String> TAGGED_USER_GROUPS = Lists.newArrayList("group1", "sdk-int-1");
    private static final ArrayList<String> FRENCH_USER_GROUPS = Lists.newArrayList("sdk-int-1");

    private static String emailPrefix;
    private static TestUser testUser;
    private static TestUser taggedUser;
    private static TestUser frenchUser;
    
    // Testing enrollment-related queries
    private static TestUser study1User;
    private static TestUser study2User;
    private static TestUser study1and2User;
    private static TestUser study1withdrawnFrom2User;
    private static TestUser study2withdrawnFrom1User;
    
    private static TestUser admin;
    private static TestUser researcher;
    private static TestUser worker;
    private static TestUser studyCoordinator;
    
    @BeforeClass
    public static void before() throws Exception {
        // Manually generate email addresses. We want to limit our AccountSummarySearch to just accounts created by
        // this test, to improve test reliability. Note that AccountSummarySearch.emailFilter uses a like
        // '%[emailFilter]%', so an email prefix works.
        emailPrefix = "bridge-testing+AccountSummarySearchTest-" + RandomStringUtils.randomAlphabetic(4) + "-";
        
        testUser = new TestUserHelper.Builder(AccountSummarySearchTest.class).withConsentUser(true)
                .withSignUp(new SignUp().email(emailPrefix + "test@sagebase.org")
                    .languages(Lists.newArrayList("es"))    
                    .dataGroups(TEST_USER_GROUPS)).createUser();
        taggedUser = new TestUserHelper.Builder(AccountSummarySearchTest.class).withConsentUser(true)
                .withSignUp(new SignUp().email(emailPrefix + "tagged@sagebase.org")
                        .languages(Lists.newArrayList("es"))
                        .roles(ImmutableList.of(Role.DEVELOPER))
                        .dataGroups(TAGGED_USER_GROUPS)).createUser();
        frenchUser = new TestUserHelper.Builder(AccountSummarySearchTest.class).withConsentUser(true)
                .withSignUp(new SignUp().email(emailPrefix + "french@sagebase.org")
                        .languages(Lists.newArrayList("fr"))
                        .attributes(ImmutableMap.of("can_be_recontacted", "true"))
                        .dataGroups(FRENCH_USER_GROUPS)).createUser();
        
        // Assign frenchUser to org1.
        admin = TestUserHelper.getSignedInAdmin();
        OrganizationsApi orgsApi = admin.getClient(OrganizationsApi.class);
        orgsApi.addMember(ORG_ID_1, frenchUser.getUserId()).execute();
        
        study1User = new TestUserHelper.Builder(AccountSummarySearchTest.class).withConsentUser(false)
                .withSignUp(new SignUp().email(emailPrefix + "s1@sagebase.org")).createUser();
        study2User = new TestUserHelper.Builder(AccountSummarySearchTest.class).withConsentUser(false)
                .withSignUp(new SignUp().email(emailPrefix + "s2@sagebase.org")).createUser();
        study1and2User = new TestUserHelper.Builder(AccountSummarySearchTest.class).withConsentUser(false)
                .withSignUp(new SignUp().email(emailPrefix + "s1and2@sagebase.org")).createUser();
        study1withdrawnFrom2User = new TestUserHelper.Builder(AccountSummarySearchTest.class).withConsentUser(false)
                .withSignUp(new SignUp().email(emailPrefix + "s1not2@sagebase.org")).createUser();
        study2withdrawnFrom1User = new TestUserHelper.Builder(AccountSummarySearchTest.class).withConsentUser(false)
                .withSignUp(new SignUp().email(emailPrefix + "s2not1@sagebase.org")).createUser();
        
        StudiesApi studiesApi = admin.getClient(StudiesApi.class);
        studiesApi.enrollParticipant(STUDY_ID_1, 
                new Enrollment().userId(study1User.getUserId()).externalId("s1-s1")).execute();
        studiesApi.enrollParticipant(STUDY_ID_1, 
                new Enrollment().userId(study1and2User.getUserId()).externalId("s1-s1and2")).execute();
        studiesApi.enrollParticipant(STUDY_ID_1, 
                new Enrollment().userId(study1withdrawnFrom2User.getUserId()).externalId("s1-s1not2")).execute();
        studiesApi.enrollParticipant(STUDY_ID_1, 
                new Enrollment().userId(study2withdrawnFrom1User.getUserId()).externalId("s1-s2not1")).execute();
        
        studiesApi.enrollParticipant(STUDY_ID_2, 
                new Enrollment().userId(study2User.getUserId()).externalId("s2-s2")).execute();
        studiesApi.enrollParticipant(STUDY_ID_2, 
                new Enrollment().userId(study1and2User.getUserId()).externalId("s2-s1and2")).execute();
        studiesApi.enrollParticipant(STUDY_ID_2, 
                new Enrollment().userId(study1withdrawnFrom2User.getUserId()).externalId("s2-s1not2")).execute();
        studiesApi.enrollParticipant(STUDY_ID_2, 
                new Enrollment().userId(study2withdrawnFrom1User.getUserId()).externalId("s2-s2not1")).execute();
        
        studiesApi.withdrawParticipant(
                STUDY_ID_1, study2withdrawnFrom1User.getUserId(), "reasons").execute();
        studiesApi.withdrawParticipant(
                STUDY_ID_2, study1withdrawnFrom2User.getUserId(), "reasons").execute();

        researcher = TestUserHelper.createAndSignInUser(AccountSummarySearchTest.class, false, RESEARCHER);
        worker = TestUserHelper.createAndSignInUser(AccountSummarySearchTest.class, false, WORKER);
        studyCoordinator = TestUserHelper.createAndSignInUser(AccountSummarySearchTest.class, false, STUDY_COORDINATOR);

        // Assign studyCoordinator to org1 for access to only study1.
        orgsApi.addMember(ORG_ID_1, studyCoordinator.getUserId()).execute();
    }

    @AfterClass
    public static void deleteTestuser() throws Exception {
        TestUser admin = TestUserHelper.getSignedInAdmin();
        ForAdminsApi forAdminsApi = admin.getClient(ForAdminsApi.class);
        ParticipantsApi participantsApi = admin.getClient(ParticipantsApi.class);
        
        List<AccountSummary> summaries = participantsApi.searchAccountSummaries(
                makeAccountSummarySearch()).execute().body().getItems();
        
        for (AccountSummary oneSummary : summaries) {
            forAdminsApi.deleteUser(oneSummary.getId()).execute();    
        }
    }
    
    @AfterClass
    public static void deleteResearcher() throws Exception {
        if (researcher != null) {
           researcher.signOutAndDeleteUser();
        }
    }
    
    @AfterClass
    public static void deleteWorker() throws Exception {
        if (worker != null) {
           worker.signOutAndDeleteUser();
        }
    }
    
    @AfterClass
    public static void deleteStudyCoordinator() throws Exception {
        if (studyCoordinator != null) {
            studyCoordinator.signOutAndDeleteUser();
        }
    }
    
    @Test
    public void testSearchingApiForResearcher() throws Exception {
        ForResearchersApi researcherApi = researcher.getClient(ForResearchersApi.class);
        testSuite(search -> researcherApi.searchAccountSummaries(search).execute().body());
    }
    
    @Test
    public void testSearchForParticipantApi() throws Exception {
        ParticipantsApi participantsApi = researcher.getClient(ParticipantsApi.class);
        testSuite(search -> participantsApi.searchAccountSummaries(search).execute().body());
    }
    
    @Test
    public void testSearchingApiForWorker() throws Exception {
        ForWorkersApi workerApi = worker.getClient(ForWorkersApi.class);
        testSuite(search -> workerApi.searchAccountSummariesForApp("api", search).execute().body());
    }

    private void testSuite(ThrowingFunction<AccountSummarySearch, AccountSummaryList> supplier) throws Exception {
        // Successful language search
        AccountSummarySearch search = makeAccountSummarySearch().language("fr");
        AccountSummaryList list = supplier.apply(search);
        Set<String> userIds = mapUserIds(list);
        assertFalse(userIds.contains(testUser.getUserId()));
        assertFalse(userIds.contains(taggedUser.getUserId()));
        assertTrue(userIds.contains(frenchUser.getUserId()));
        assertEquals("fr", list.getRequestParams().getLanguage());
        
        // verify the French testUser has attributes
        AccountSummary summary = list.getItems().stream()
            .filter(sum -> !sum.getId().equals(testUser.getUserId()))
            .findFirst().get();
        assertEquals("true", summary.getAttributes().get("can_be_recontacted"));
        
        // Unsuccessful language search
        search = makeAccountSummarySearch().language("en");
        list = supplier.apply(search);
        userIds = mapUserIds(list);
        assertFalse(userIds.contains(testUser.getUserId()));
        assertFalse(userIds.contains(taggedUser.getUserId()));
        assertFalse(userIds.contains(frenchUser.getUserId()));
        assertEquals("en", list.getRequestParams().getLanguage());
        
        // Successful "allOfGroups" search
        search = makeAccountSummarySearch().allOfGroups(TAGGED_USER_GROUPS);
        list = supplier.apply(search);
        userIds = mapUserIds(list);
        assertFalse(userIds.contains(testUser.getUserId()));
        assertTrue(userIds.contains(taggedUser.getUserId()));
        assertFalse(userIds.contains(frenchUser.getUserId()));
        listsMatch(TAGGED_USER_GROUPS, list.getRequestParams().getAllOfGroups());
        
        // This is a data group that spans all three accounts..
        search = makeAccountSummarySearch().allOfGroups(FRENCH_USER_GROUPS);
        list = supplier.apply(search);
        userIds = mapUserIds(list);
        assertTrue(userIds.contains(testUser.getUserId()));
        assertTrue(userIds.contains(taggedUser.getUserId()));
        assertTrue(userIds.contains(frenchUser.getUserId()));
        listsMatch(FRENCH_USER_GROUPS, list.getRequestParams().getAllOfGroups());
        
        // This pulls up nothing
        search = makeAccountSummarySearch().allOfGroups(Lists.newArrayList("sdk-int-2"));
        list = supplier.apply(search);
        userIds = mapUserIds(list);
        assertFalse(userIds.contains(testUser.getUserId()));
        assertFalse(userIds.contains(taggedUser.getUserId()));
        assertFalse(userIds.contains(frenchUser.getUserId()));
        listsMatch(Lists.newArrayList("sdk-int-2"), list.getRequestParams().getAllOfGroups());
        
        // Successful "noneOfGroups" search
        search = makeAccountSummarySearch().noneOfGroups(Lists.newArrayList("group1")).pageSize(100);
        list = supplier.apply(search);
        userIds = mapUserIds(list);
        assertTrue(userIds.contains(testUser.getUserId()));
        assertFalse(userIds.contains(taggedUser.getUserId()));
        assertTrue(userIds.contains(frenchUser.getUserId()));
        listsMatch(Lists.newArrayList("group1"), list.getRequestParams().getNoneOfGroups());
        
        // This is a data group that spans all three accounts..
        search = makeAccountSummarySearch().noneOfGroups(FRENCH_USER_GROUPS);
        list = supplier.apply(search);
        userIds = mapUserIds(list);
        assertFalse(userIds.contains(testUser.getUserId()));
        assertFalse(userIds.contains(taggedUser.getUserId()));
        assertFalse(userIds.contains(frenchUser.getUserId()));
        listsMatch(FRENCH_USER_GROUPS, list.getRequestParams().getNoneOfGroups());
        
        // This pulls up everything we're looking for
        search = makeAccountSummarySearch().noneOfGroups(Lists.newArrayList("sdk-int-2")).pageSize(100);
        list = supplier.apply(search);
        userIds = mapUserIds(list);
        assertTrue(userIds.contains(testUser.getUserId()));
        assertTrue(userIds.contains(taggedUser.getUserId()));
        assertTrue(userIds.contains(frenchUser.getUserId()));
        listsMatch(Lists.newArrayList("sdk-int-2"), list.getRequestParams().getNoneOfGroups());
        
        // mixed works
        search = makeAccountSummarySearch().allOfGroups(Lists.newArrayList("sdk-int-1"))
                .noneOfGroups(Lists.newArrayList("group1"));
        list = supplier.apply(search);
        userIds = mapUserIds(list);
        assertTrue(userIds.contains(testUser.getUserId()));
        assertFalse(userIds.contains(taggedUser.getUserId()));
        assertTrue(userIds.contains(frenchUser.getUserId()));
        listsMatch(Lists.newArrayList("sdk-int-1"), list.getRequestParams().getAllOfGroups());
        listsMatch(Lists.newArrayList("group1"), list.getRequestParams().getNoneOfGroups());
        
        search = makeAccountSummarySearch().allOfGroups(Lists.newArrayList("sdk-int-1")).language("fr")
                .noneOfGroups(Lists.newArrayList("sdk-int-2"));
        list = supplier.apply(search);
        userIds = mapUserIds(list);
        assertFalse(userIds.contains(testUser.getUserId()));
        assertFalse(userIds.contains(taggedUser.getUserId()));
        assertTrue(userIds.contains(frenchUser.getUserId()));

        RequestParams rp = list.getRequestParams();
        assertEquals(rp.getLanguage(), "fr");
        assertEquals(rp.getAllOfGroups(), ImmutableList.of("sdk-int-1"));
        assertEquals(rp.getNoneOfGroups(), ImmutableList.of("sdk-int-2"));

        // tagged user has a role, the other two do not.
        search = makeAccountSummarySearch().adminOnly(true);
        list = supplier.apply(search);
        userIds = mapUserIds(list);
        assertFalse(userIds.contains(testUser.getUserId()));
        assertTrue(userIds.contains(taggedUser.getUserId()));
        assertFalse(userIds.contains(frenchUser.getUserId()));
        
        rp = list.getRequestParams();
        assertTrue(rp.isAdminOnly());
        
        search = makeAccountSummarySearch().adminOnly(false);
        list = supplier.apply(search);
        userIds = mapUserIds(list);
        assertTrue(userIds.contains(testUser.getUserId()));
        assertFalse(userIds.contains(taggedUser.getUserId()));
        assertTrue(userIds.contains(frenchUser.getUserId()));
        
        search = makeAccountSummarySearch().orgMembership(ORG_ID_1);
        list = supplier.apply(search);
        userIds = mapUserIds(list);
        assertFalse(userIds.contains(testUser.getUserId()));
        assertFalse(userIds.contains(taggedUser.getUserId()));
        assertTrue(userIds.contains(frenchUser.getUserId()));
        
        search = makeAccountSummarySearch().orgMembership(ORG_ID_2);
        list = supplier.apply(search);
        userIds = mapUserIds(list);
        assertFalse(userIds.contains(testUser.getUserId()));
        assertFalse(userIds.contains(taggedUser.getUserId()));
        assertFalse(userIds.contains(frenchUser.getUserId()));
        
        rp = list.getRequestParams();
        assertEquals(rp.getOrgMembership(), ORG_ID_2);
        assertEquals(rp.getEmailFilter(), emailPrefix);
    }
    
    // These depend on the collection of enrollment records, so we particularly want to test
    // that we are matching on the correct study when calling study-scoped apis.
    @Test
    public void searchOnEnrollmentFieldsForResearcher() throws Exception { 
        StudyParticipantsApi studyParticipantsApi = researcher.getClient(StudyParticipantsApi.class);

        String s1 = study1User.getUserId();
        String s2 = study2User.getUserId();
        String s1and2 = study1and2User.getUserId();
        String s1not2 = study1withdrawnFrom2User.getUserId();
        String s2not1 = study2withdrawnFrom1User.getUserId();
        
        AccountSummarySearch search = makeAccountSummarySearch();
        
        // With no enrollment filtering
        AccountSummaryList list = studyParticipantsApi.getStudyParticipants(STUDY_ID_1, search).execute().body();
        Set<String> found = mapUserIds(list);
        assertTrue(found.containsAll(ImmutableSet.of(s1, s1and2, s1not2, s2not1)));
        assertFalse(found.containsAll(ImmutableSet.of(s2)));

        list = studyParticipantsApi.getStudyParticipants(STUDY_ID_2, search).execute().body();
        found = mapUserIds(list);
        assertTrue(found.containsAll(ImmutableSet.of(s2, s1and2, s1not2, s2not1)));
        assertFalse(found.containsAll(ImmutableSet.of(s1)));
        
        // Filter on enrolled/withdrawn
        search.enrollment(WITHDRAWN);
        list = studyParticipantsApi.getStudyParticipants(STUDY_ID_1, search).execute().body();
        found = mapUserIds(list);
        assertTrue(found.containsAll(ImmutableSet.of(s2not1)));
        assertFalse(found.containsAll(ImmutableSet.of(s1, s2, s1and2, s1not2)));

        search.enrollment(WITHDRAWN);
        list = studyParticipantsApi.getStudyParticipants(STUDY_ID_2, search).execute().body();
        found = mapUserIds(list);
        assertTrue(found.containsAll(ImmutableSet.of(s1not2)));
        assertFalse(found.containsAll(ImmutableSet.of(s1, s2, s1and2, s2not1)));

        search.enrollment(ENROLLED);
        list = studyParticipantsApi.getStudyParticipants(STUDY_ID_1, search).execute().body();
        found = mapUserIds(list);
        assertTrue(found.containsAll(ImmutableSet.of(s1, s1and2, s1not2)));
        assertFalse(found.containsAll(ImmutableSet.of(s2, s2not1)));

        search.enrollment(ENROLLED);
        list = studyParticipantsApi.getStudyParticipants(STUDY_ID_2, search).execute().body();
        found = mapUserIds(list);
        assertTrue(found.containsAll(ImmutableSet.of(s2, s1and2, s2not1)));
        assertFalse(found.containsAll(ImmutableSet.of(s1, s1not2)));
        
        // external ID searches
        search =  makeAccountSummarySearch().externalIdFilter("s1-");
        list = studyParticipantsApi.getStudyParticipants(STUDY_ID_1, search).execute().body();
        found = mapUserIds(list);
        assertTrue(found.containsAll(ImmutableSet.of(s1, s1and2, s1not2, s2not1)));
        assertFalse(found.containsAll(ImmutableSet.of(s2)));

        search.enrollment(null).externalIdFilter("s1-");
        list = studyParticipantsApi.getStudyParticipants(STUDY_ID_2, search).execute().body();
        assertTrue(list.getItems().isEmpty());
        
        search.enrollment(null).externalIdFilter("s1-s1");
        list = studyParticipantsApi.getStudyParticipants(STUDY_ID_1, search).execute().body();
        found = mapUserIds(list);
        assertTrue(found.containsAll(ImmutableSet.of(s1, s1and2, s1not2)));
        assertFalse(found.containsAll(ImmutableSet.of(s2, s2not1)));
        
        // status matches. Admin has to toggle status.
        StudyParticipantsApi adminSPApi = admin.getClient(StudyParticipantsApi.class);
        StudyParticipant participant1 = adminSPApi.getStudyParticipantById(
                STUDY_ID_1, study1User.getUserId(), false).execute().body();
        participant1.setStatus(AccountStatus.DISABLED);
        StudyParticipant participant2 = adminSPApi.getStudyParticipantById(
                STUDY_ID_1, study1and2User.getUserId(), false).execute().body();
        participant2.setStatus(AccountStatus.DISABLED);
        adminSPApi.updateStudyParticipant(STUDY_ID_1, participant1.getId(), participant1).execute();
        adminSPApi.updateStudyParticipant(STUDY_ID_1, participant2.getId(), participant2).execute();
        
        // resuming with our original researcher
        search = makeAccountSummarySearch().status(AccountStatus.DISABLED);
        list = studyParticipantsApi.getStudyParticipants(STUDY_ID_1, search).execute().body();
        found = mapUserIds(list);
        assertTrue(found.containsAll(ImmutableSet.of(s1, s1and2)));
        assertFalse(found.containsAll(ImmutableSet.of(s2, s1not2, s2not1)));
    }

    @Test
    public void searchOnEnrollmentFieldsForStudyCoordinator() throws Exception {
        StudyParticipantsApi studyParticipantsApi = studyCoordinator.getClient(StudyParticipantsApi.class);
        
        String s1 = study1User.getUserId();
        String s2 = study2User.getUserId();
        String s1and2 = study1and2User.getUserId();
        String s1not2 = study1withdrawnFrom2User.getUserId();
        String s2not1 = study2withdrawnFrom1User.getUserId();
        
        AccountSummarySearch search = makeAccountSummarySearch();
        
        // With no enrollment filtering
        AccountSummaryList list = studyParticipantsApi.getStudyParticipants(STUDY_ID_1, search).execute().body();
        Set<String> found = mapUserIds(list);
        assertTrue(found.containsAll(ImmutableSet.of(s1, s1and2, s1not2, s2not1)));
        assertFalse(found.containsAll(ImmutableSet.of(s2)));

        // Calling the study 2 API doesn't even work, so those tests can be skipped. But verify this is
        // true first.
        try {
            studyParticipantsApi.getStudyParticipants(STUDY_ID_2, search).execute().body();
            fail("Should have thrown exception");
        } catch(UnauthorizedException e) { 
        }
        
        // Filter on enrolled/withdrawn
        search.enrollment(WITHDRAWN);
        list = studyParticipantsApi.getStudyParticipants(STUDY_ID_1, search).execute().body();
        found = mapUserIds(list);
        assertTrue(found.containsAll(ImmutableSet.of(s2not1)));
        assertFalse(found.containsAll(ImmutableSet.of(s1, s2, s1and2, s1not2)));

        search.enrollment(ENROLLED);
        list = studyParticipantsApi.getStudyParticipants(STUDY_ID_1, search).execute().body();
        found = mapUserIds(list);
        assertTrue(found.containsAll(ImmutableSet.of(s1, s1and2, s1not2)));
        assertFalse(found.containsAll(ImmutableSet.of(s2, s2not1)));

        // external ID searches
        search =  makeAccountSummarySearch().externalIdFilter("s1-");
        list = studyParticipantsApi.getStudyParticipants(STUDY_ID_1, search).execute().body();
        found = mapUserIds(list);
        assertTrue(found.containsAll(ImmutableSet.of(s1, s1and2, s1not2, s2not1)));
        assertFalse(found.containsAll(ImmutableSet.of(s2)));

        search.enrollment(null).externalIdFilter("s1-s1");
        list = studyParticipantsApi.getStudyParticipants(STUDY_ID_1, search).execute().body();
        found = mapUserIds(list);
        assertTrue(found.containsAll(ImmutableSet.of(s1, s1and2, s1not2)));
        assertFalse(found.containsAll(ImmutableSet.of(s2, s2not1)));
        
        // status matches. Admin has to toggle status.
        StudyParticipantsApi adminSPApi = admin.getClient(StudyParticipantsApi.class);
        StudyParticipant participant1 = adminSPApi.getStudyParticipantById(
                STUDY_ID_1, study1User.getUserId(), false).execute().body();
        participant1.setStatus(AccountStatus.DISABLED);
        StudyParticipant participant2 = adminSPApi.getStudyParticipantById(
                STUDY_ID_1, study1and2User.getUserId(), false).execute().body();
        participant2.setStatus(AccountStatus.DISABLED);
        adminSPApi.updateStudyParticipant(STUDY_ID_1, participant1.getId(), participant1).execute();
        adminSPApi.updateStudyParticipant(STUDY_ID_1, participant2.getId(), participant2).execute();
        
        // resuming with our original study coordinator
        search = makeAccountSummarySearch().status(AccountStatus.DISABLED);
        list = studyParticipantsApi.getStudyParticipants(STUDY_ID_1, search).execute().body();
        found = mapUserIds(list);
        assertTrue(found.containsAll(ImmutableSet.of(s1, s1and2)));
        assertFalse(found.containsAll(ImmutableSet.of(s2, s1not2, s2not1)));
    }
    
    private static AccountSummarySearch makeAccountSummarySearch() {
        return new AccountSummarySearch().emailFilter(emailPrefix);
    }

    private void listsMatch(List<String> list1, List<String> list2) {
        if (list1.size() != list2.size()) {
            fail("Lists are not the same size, cannot be equal");
        }
        assertEquals(Sets.newHashSet(list1), Sets.newHashSet(list2));
    }
    
    private Set<String> mapUserIds(AccountSummaryList list) {
        return list.getItems().stream().map(AccountSummary::getId).collect(Collectors.toSet());
    }
}
