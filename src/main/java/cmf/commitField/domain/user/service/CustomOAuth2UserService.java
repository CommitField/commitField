package cmf.commitField.domain.user.service;

import cmf.commitField.domain.commit.sinceCommit.service.CommitCacheService;
import cmf.commitField.domain.commit.totalCommit.service.TotalCommitService;
import cmf.commitField.domain.pet.entity.Pet;
import cmf.commitField.domain.pet.repository.PetRepository;
import cmf.commitField.domain.user.entity.CustomOAuth2User;
import cmf.commitField.domain.user.entity.User;
import cmf.commitField.domain.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {
    private final UserRepository userRepository;
    private final PetRepository petRepository;
    private final HttpServletRequest request;  // HttpServletRequest를 주입 받음.
    private final CommitCacheService commitCacheService;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) {
        OAuth2User oauthUser = super.loadUser(userRequest);
        TotalCommitService totalCommitService = new TotalCommitService();

        Map<String, Object> attributes = oauthUser.getAttributes();
        String username = (String) attributes.get("login");  // GitHub ID
        String email = (String) attributes.get("email");
        String name = (String) attributes.get("name");
        String avatarUrl = (String) attributes.get("avatar_url");

        // 이메일이 없는 경우를 대비하여 기본값 설정
        if (email == null) {
            email = username + "@github.com";
        }

        Optional<User> existingUser = userRepository.findByUsername(username);

        User user;
        Pet pet;
        if (existingUser.isPresent()) {
            //유저 정보가 있다면 유저 정보를 얻어온다.
            user = existingUser.get();
            user.setAvatarUrl(avatarUrl);
            user.setEmail(email);  // GitHub에서 이메일이 변경될 수도 있으니 업데이트
            user.setStatus(true);  // 로그인 시 status를 true로 설정
            userRepository.save(user);  // 저장
        } else {
            //유저 정보가 db에 존재하지 않을 경우 회원가입 시킨다.
            //유저 생성 및 펫 생성
            user = new User(username, email, name, avatarUrl,true, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
            userRepository.save(user);

            pet = new Pet("알알", user); // 변경 필요
            petRepository.save(pet);

            user.addPets(pet);
            user.setLastCommitCount(totalCommitService.getTotalCommitCount(user.getUsername()).getTotalCommitContributions());
            // 가입 시점의 전체 커밋 수를 계산

            // 회원가입한 유저는 커밋 기록에 상관없이 Redis에 입력해둔다.
            commitCacheService.updateCachedCommitCount(user.getUsername(),0);
        }

        // 세션에 사용자 정보 저장
        HttpSession session = request.getSession();
        session.setAttribute("username", user.getUsername());
        return new CustomOAuth2User(oauthUser, user);
    }

    // id로 user 조회
    public Optional<User> getUserById(Long userId) {
        return userRepository.findById(userId);
    }

    // email로 user 조회
    public Optional<User> getUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }
}
