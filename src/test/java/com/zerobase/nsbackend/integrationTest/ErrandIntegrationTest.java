package com.zerobase.nsbackend.integrationTest;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

import com.zerobase.nsbackend.errand.domain.entity.Errand;
import com.zerobase.nsbackend.errand.domain.entity.ErrandHashtag;
import com.zerobase.nsbackend.errand.domain.entity.ErrandImage;
import com.zerobase.nsbackend.errand.domain.entity.LikedMember;
import com.zerobase.nsbackend.errand.domain.repository.ErrandRepository;
import com.zerobase.nsbackend.errand.domain.vo.ErrandStatus;
import com.zerobase.nsbackend.errand.domain.vo.PayDivision;
import com.zerobase.nsbackend.errand.dto.ErrandChangAddressRequest;
import com.zerobase.nsbackend.errand.dto.ErrandCreateRequest;
import com.zerobase.nsbackend.errand.dto.ErrandDto;
import com.zerobase.nsbackend.errand.dto.ErrandUpdateRequest;
import com.zerobase.nsbackend.errand.dto.ErranderDto;
import com.zerobase.nsbackend.global.auth.AuthManager;
import com.zerobase.nsbackend.global.exceptionHandle.ErrorCode;
import com.zerobase.nsbackend.global.vo.Address;
import com.zerobase.nsbackend.member.domain.Member;
import com.zerobase.nsbackend.member.repository.MemberRepository;
import com.zerobase.nsbackend.member.type.Authority;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

@WithMockUser
@DisplayName("의뢰 통합 테스트")
class ErrandIntegrationTest extends IntegrationTest {
  SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
  @Autowired
  MemberRepository memberRepository;
  @Autowired
  ErrandRepository errandRepository;
  @MockBean
  AuthManager authManager;

  Member member01;
  Member member02;

  ErrandCreateRequest createRequest1;
  @BeforeEach
  void init() throws ParseException {
    member01 = Member.builder()
        .email("member01")
        .password("password")
        .authority(Authority.ROLE_USER)
        .build();
    member02 = Member.builder()
        .email("member02")
        .password("password")
        .authority(Authority.ROLE_USER)
        .build();
    memberRepository.save(member01);
    memberRepository.save(member02);

    createRequest1 = ErrandCreateRequest.builder()
        .title("testTitle")
        .content("testContent")
        .payDivision(PayDivision.HOURLY)
        .pay(10000)
        .deadLine(dateFormat.parse("2024-01-29"))
        .hashtags(List.of("알바", "이사"))
        .build();
  }

  @Test
  @DisplayName("의뢰 생성에 성공합니다 (첨부 사진 없는 경우)")
  void createErrand_success_when_no_images() throws Exception {
    // given
    when(authManager.getUsername()).thenReturn(member01.getEmail());

    // when
    ResultActions resultActions = requestCreateErrand(createRequest1);

    // then
    resultActions
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", CoreMatchers.startsWith("/errands/")));
  }

  @Test
  @DisplayName("의뢰 생성에 성공합니다 (첨부사진 있는 경우)")
  void createErrand_success_with_images() throws Exception {
    // given
    MockMultipartFile file1 = makeMultipartFile();
    MockMultipartFile file2 = makeMultipartFile();

    ErrandCreateRequest createRequest = createRequest1;
    when(authManager.getUsername()).thenReturn(member01.getEmail());

    MockMultipartFile jsonRequest = new MockMultipartFile("errand", "",
        "application/json", asJsonString(createRequest).getBytes());

    // when
    ResultActions resultActions = mvc.perform(
            multipart("/errands")
                .file(file1)
                .file(file2)
                .file(jsonRequest)
                .contentType(MediaType.MULTIPART_FORM_DATA)
        )
        .andDo(print());

    // then
    MvcResult result = resultActions
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", CoreMatchers.startsWith("/errands/")))
        .andReturn();

    Long newId = parseNewId(result);
    Errand newErrand = errandRepository.findById(newId)
        .orElseThrow(() -> new RuntimeException("test fail"));
    assertThat(newErrand.getTitle()).isEqualTo(createRequest.getTitle());
    assertThat(newErrand.getContent()).isEqualTo(createRequest.getContent());
    assertThat(newErrand.getImages()).hasSize(2);
    assertThat(newErrand.getPayDivision()).isEqualTo(createRequest.getPayDivision());
    assertThat(newErrand.getPay()).isEqualTo(createRequest.getPay());

    // 테스트로 업로드된 파일 삭제
    removeFilesAfterTest(newErrand.getImages().stream().map(ErrandImage::getImageUrl)
        .collect(Collectors.toList()));
  }

  @Test
  @DisplayName("의뢰 ID로 의뢰 조회에 성공합니다.")
  void readErrandById_success() throws Exception {
    // given
    Long errandId = createErrandForGiven(createRequest1);

    // when
    MvcResult mvcResult = requestGetErrand(errandId)
        .andReturn();

    // then
    assertThat(mvcResult.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());

    MockHttpServletResponse mockHttpServletResponse = mvcResult.getResponse();
    mockHttpServletResponse.setCharacterEncoding("UTF-8");
    String contentAsString = mockHttpServletResponse.getContentAsString();
    ErrandDto result = objectMapper.readValue(contentAsString, ErrandDto.class);

    assertThat(result.getTitle()).isEqualTo(createRequest1.getTitle());
    assertThat(result.getContent()).isEqualTo(createRequest1.getContent());
    assertThat(result.getPayDivision()).isEqualTo(createRequest1.getPayDivision());
    assertThat(result.getPay()).isEqualTo(createRequest1.getPay());
    assertThat(result.getHashtags()).usingRecursiveComparison().isEqualTo(createRequest1.getHashtags());
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    assertThat(result.getDeadLine().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
        .isEqualTo(dateFormat.format(createRequest1.getDeadLine()));
    assertThat(result.getViewCount()).isEqualTo(1);
    assertThat(result.getCreatedAt()).isNotNull();
    assertThat(result.getStatus()).isEqualTo(ErrandStatus.REQUEST);
  }

  @Test
  @DisplayName("의뢰ID로 의뢰 조회 시, 의뢰 ID가 없어서 실패합니다.")
  void readErrandById_fail_because_errand_not_found() throws Exception {
    // given
    Long errandId = createErrandForGiven(createRequest1);

    Long notFoundId = 100L;

    // when
    ResultActions resultActions = requestGetErrand(notFoundId);

    // then
    resultActions
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("errorCode").value(is(ErrorCode.ERRAND_NOT_FOUND.getCode())));
  }

  @Test
  @DisplayName("의뢰 성공적으로 수정합니다.")
  void updateErrand_success() throws Exception {
    // given
    Long errandId = createErrandForGiven(createRequest1);

    ErrandUpdateRequest updateRequest = ErrandUpdateRequest.builder()
        .title("updatedTitle")
        .content("updatedContent")
        .payDivision(PayDivision.UNIT)
        .pay(10000)
        .build();
    when(authManager.getUsername()).thenReturn(member01.getEmail());

    // when
    ResultActions resultActions = mvc.perform(
        put("/errands/{id}", errandId)
            .with(user(member01.getEmail()))
            .content(asJsonString(updateRequest))
            .contentType(MediaType.APPLICATION_JSON)
    );

    // then
    resultActions
        .andExpect(status().isOk());
  }

  @WithMockUser(roles = "USER")
  @Test
  @DisplayName("의뢰 성공적으로 수정 시, 의뢰자가 아니라서 실패합니다.")
  void updateErrand_fail_because_not_errander() throws Exception {
    // given
    Long errandId = createErrandForGiven(createRequest1);

    ErrandUpdateRequest updateRequest = ErrandUpdateRequest.builder()
        .title("updatedTitle")
        .content("updatedContent")
        .payDivision(PayDivision.UNIT)
        .pay(10000)
        .build();
    when(authManager.getUsername()).thenReturn(member02.getEmail());

    // when
    ResultActions resultActions = mvc.perform(
        put("/errands/{id}", errandId)
            .content(asJsonString(updateRequest))
            .contentType(MediaType.APPLICATION_JSON)
    );

    // then
    resultActions
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("의뢰 취소에 성공합니다.")
  void cancelErrand_success() throws Exception {
    Long errandId = createErrandForGiven(createRequest1);

    ResultActions perform = mvc.perform(
        post("/errands/{id}/cancel", errandId)
        .with(user(member01.getEmail())));

    perform.andExpect(status().isOk());
  }

  @Test
  @DisplayName("의뢰자가 아니라서 의뢰 취소에 실패합니다.")
  void cancelErrand_fail_because_not_errander() throws Exception {
    Long errandId = createErrandForGiven(createRequest1);
    when(authManager.getUsername()).thenReturn(member02.getEmail());

    ResultActions perform = mvc.perform(
        post("/errands/{id}/cancel", errandId))
            .andDo(print());
    perform.andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("의뢰에 해쉬태그를 추가합니다.")
  void addHashTag_success() throws Exception {
    // given
    Long errandId = createErrandForGiven(createRequest1);
    String tag = "알바";

    // when
    ResultActions perform = requestAddHashtag(errandId, tag);

    // then
    perform.andExpect(status().isOk());

    Errand errand = errandRepository.findById(errandId)
        .orElseThrow(() -> new RuntimeException("test fail"));
    Set<ErrandHashtag> hashtags = errand.getHashtags();
    assertThat(hashtags).contains(ErrandHashtag.of(tag));
  }

  @Test
  @DisplayName("의뢰에 해쉬태그를 제거합니다.")
  void deleteHashTag_success() throws Exception {
    // given
    Long errandId = createErrandForGiven(createRequest1);
    String tag = "알바";
    requestAddHashtag(errandId, tag);

    // when
    ResultActions perform = mvc.perform(
            delete("/errands/{id}/hashtag", errandId)
                .param("tag", tag)
        )
        .andDo(print());

    // then
    perform.andExpect(status().isOk());

    Errand errand = errandRepository.findById(errandId)
        .orElseThrow(() -> new RuntimeException("test fail"));
    Set<ErrandHashtag> hashtags = errand.getHashtags();
    assertThat(hashtags).doesNotContain(ErrandHashtag.of(tag));
  }

  @Test
  @DisplayName("의뢰 주소를 수정합니다.")
  void changeErrandAddress_success() throws Exception {
    // given
    Long errandId = createErrandForGiven(createRequest1);
    ErrandChangAddressRequest request = new ErrandChangAddressRequest(
        "서울시 강남구", 123.123, 123.123);

    // when
    ResultActions perform = mvc.perform(
            put("/errands/{id}/address", errandId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(request))
        )
        .andDo(print());

    // then
    perform.andExpect(status().isOk());

    Errand errand = errandRepository.findById(errandId)
        .orElseThrow(() -> new RuntimeException("test fail"));
    Address address = errand.getAddress();
    assertThat(address).usingRecursiveComparison()
        .isEqualTo(request);
  }

  @Test
  @DisplayName("의뢰 좋아요에 성공합니다.")
  void likeErrand_on_success() throws Exception {
    // given
    Long errandId = createErrandForGiven(createRequest1);

    // when
    ResultActions perform = mvc.perform(
            post("/errands/{id}/like", errandId)
        )
        .andDo(print());

    // then
    perform.andExpect(status().isOk());

    Errand errand = errandRepository.findById(errandId)
        .orElseThrow(() -> new RuntimeException("test fail"));
    Set<LikedMember> likedMembers = errand.getLikedMembers();

    assertThat(likedMembers).contains(LikedMember.of(errand, member01));
  }

  @Test
  @DisplayName("의뢰 좋아요 취소에 성공합니다.")
  void likeErrand_cancel_success() throws Exception {
    // given
    Long errandId = createErrandForGiven(createRequest1);
    mvc.perform(
        post("/errands/{id}/like", errandId)
    );

    // when
    ResultActions perform = mvc.perform(
            post("/errands/{id}/like", errandId)
        )
        .andDo(print());

    // then
    perform.andExpect(status().isOk());

    Errand errand = errandRepository.findById(errandId)
        .orElseThrow(() -> new RuntimeException("test fail"));
    Set<LikedMember> likedMembers = errand.getLikedMembers();

    assertThat(likedMembers).doesNotContain(LikedMember.of(errand, member01));
  }

  @Test
  @DisplayName("의뢰자 정보를 조회합니다.")
  void readErranderSuccess() throws Exception {
    // given
    Long errandId = createErrandForGiven(createRequest1);

    // when
    MvcResult mvcResult = mvc.perform(
            get("/errands/{id}/errander", errandId)
        )
        .andDo(print())
        .andReturn();

    // then
    assertThat(mvcResult.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());

    MockHttpServletResponse mockHttpServletResponse = mvcResult.getResponse();
    mockHttpServletResponse.setCharacterEncoding("UTF-8");
    String contentAsString = mockHttpServletResponse.getContentAsString();
    ErranderDto result = objectMapper.readValue(contentAsString, ErranderDto.class);

    assertThat(result.getMemberId()).isEqualTo(member01.getId());
    assertThat(result.getNickname()).isEqualTo(member01.getNickname());
    assertThat(result.getEmail()).isEqualTo(member01.getEmail());
    assertThat(result.getProfileImage()).isEqualTo(member01.getProfileImage());
    assertThat(result.getErrandCount()).isEqualTo(1);
  }

  private ResultActions requestCreateErrand(ErrandCreateRequest request)
      throws Exception {

    MockMultipartFile jsonRequest = new MockMultipartFile("errand", "",
        "application/json", asJsonString(request).getBytes());

    return mvc.perform(
        multipart("/errands")
            .file(jsonRequest)
            .contentType(MediaType.MULTIPART_FORM_DATA)
    );
  }

  /**
   * 테스트 준비용 의뢰 생성 요청을 보냅니다.
   *
   * @return 생성된 의뢰의 id
   * @throws Exception
   */
  private Long createErrandForGiven(ErrandCreateRequest request)
      throws Exception {
    when(authManager.getUsername()).thenReturn(member01.getEmail());

    MvcResult mvcResult = requestCreateErrand(request)
        .andReturn();

    return parseNewId(mvcResult);
  }

  private Long parseNewId(MvcResult mvcResult) {
    String newErrandUri = mvcResult.getResponse().getHeader("Location");
    String newErrandId = newErrandUri.replace("/errands/", "");
    return Long.parseLong(newErrandId);
  }

  private ResultActions requestGetErrand(Long errandId) throws Exception {
    return mvc.perform(
            get("/errands/{id}", errandId)
                .contentType(MediaType.APPLICATION_JSON)
    );
  }

  private static MockMultipartFile makeMultipartFile() {
    return new MockMultipartFile(
        "images",
        "hello.txt",
        MediaType.TEXT_PLAIN_VALUE,
        "Hello, World!".getBytes()
    );
  }

  /**
   * 리스트에 담긴 파일이름에 해당하는 파일을 삭체처리합니다.
   * @param fileNames
   * @throws Exception
   */
  public void removeFilesAfterTest(List<String> fileNames) throws Exception {
    for(String fileName: fileNames) {
      mvc.perform(delete("/images/{filename}", fileName));
    }
  }

  /**
   * 의뢰 해쉬태그 삭제 요청을 보냅니다.
   * @param errandId
   * @param tag
   * @return
   * @throws Exception
   */
  private ResultActions requestAddHashtag(Long errandId, String tag) throws Exception {
    ResultActions perform = mvc.perform(
            put("/errands/{id}/hashtag", errandId)
                .param("tag", tag)
        )
        .andDo(print());
    return perform;
  }
}
