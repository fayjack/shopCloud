
package com.shop.web;

import com.google.common.base.Preconditions;
import com.shop.entity.admin.Admin;
import com.shop.security.app.social.AppSingUpUtils;
import com.shop.security.core.properties.SecurityProperties;
import com.shop.service.userToken.UacUserTokenService;
import com.shop.support.BaseController;
import com.shop.utils.RequestUtil;
import com.shop.wrapper.WrapMapper;
import com.shop.wrapper.Wrapper;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.common.exceptions.UnapprovedClientAuthenticationException;
import org.springframework.security.oauth2.provider.ClientDetails;
import org.springframework.security.oauth2.provider.ClientDetailsService;
import org.springframework.security.oauth2.provider.token.TokenStore;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.ServletWebRequest;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.UnsupportedEncodingException;

/**
 *
 *
 */
@RestController
@RequestMapping("/user")
@Api("用户测试类")
public class UserController extends BaseController {

	@Autowired
	private AppSingUpUtils appSingUpUtils;

	@Autowired
	private ClientDetailsService clientDetailsService;

	@Autowired
	private UacUserTokenService uacUserTokenService;


	private static final String BEARER_TOKEN_TYPE = "Basic ";


	@Autowired
	private SecurityProperties securityProperties;

	//这里模拟第三方用户注册> 登录
	@PostMapping("/regist")
	public void regist(Admin user, HttpServletRequest request) {

		//不管是注册用户还是绑定用户，都会拿到一个用户唯一标识。
		String userId = user.getUsername();
		//providerSignInUtils.doPostSignUp(userId, new ServletWebRequest(request));
		appSingUpUtils.doPostSignUp(new ServletWebRequest(request), userId);
	}

	@Resource
	private TokenStore tokenStore;

	/**
	 * @description: 校验token是否有效
	 * @author haoran.zhang
	 * @date 2020/04/11 15:06
	 * @param [token]
	 * @return com.shop.wrapper.Wrapper
	 */
	@RequestMapping({"/oauth/check_token"})
	public Wrapper checkToken(@RequestParam("token") String token) {
		OAuth2AccessToken oAuth2AccessToken = tokenStore.readAccessToken(token);
		int expiresIn = oAuth2AccessToken.getExpiresIn();
		if (expiresIn < 0) {
			return WrapMapper.ok(false);
		}
		return WrapMapper.ok(true);
	}

	
	@GetMapping("/me")
	public Object getCurrentUser(Authentication user, HttpServletRequest request) throws ExpiredJwtException, UnsupportedJwtException, MalformedJwtException, SignatureException, IllegalArgumentException, UnsupportedEncodingException {
		
//		String token = StringUtils.substringAfter(request.getHeader("Authorization"), "Bearer ");
//		
//		Claims claims = Jwts.parser().setSigningKey(securityProperties.getOauth2().getJwtSigningKey().getBytes("UTF-8"))
//					.parseClaimsJws(token).getBody();
//		
//		String company = (String) claims.get("company");
//		
//		System.out.println(company);
		
		return user;
	}


	/**
	 * 登出.
	 *
	 * @param accessToken the access token
	 *
	 * @return the wrapper
	 */
	@PostMapping(value = "/logout")
	@ApiOperation(httpMethod = "POST", value = "登出")
	public Wrapper loginAfter(String access_token,String refresh_token) {
		logger.info("access_token{},refresh_token{}",access_token,refresh_token);
		if (!StringUtils.isEmpty(access_token)) {
			// 修改用户在线状态
			uacUserTokenService.logout(access_token,refresh_token);
		}
		return WrapMapper.ok("退出登录成功");
	}

	/**
	 * 刷新token.
	 *
	 * @param request      the request
	 * @param refresh_token the refresh token
	 * @param access_token  the access_token
	 * @return the wrapper
	 */
	@PostMapping(value = "/auth/refreshToken")
	@ApiOperation(httpMethod = "POST", value = "刷新token")
	public Wrapper<String> refreshToken(HttpServletRequest request, @RequestParam(value = "refresh_token") String refresh_token,@RequestParam(value = "access_token") String access_token) {
		String token;
		try {
			Preconditions.checkArgument(org.apache.commons.lang3.StringUtils.isNotEmpty(refresh_token), "refresh_token is null");
			Preconditions.checkArgument(org.apache.commons.lang3.StringUtils.isNotEmpty(access_token), "access_token is null");

			String header = request.getHeader(HttpHeaders.AUTHORIZATION);
			if (header == null || !header.startsWith(BEARER_TOKEN_TYPE)) {
				throw new UnapprovedClientAuthenticationException("请求头中无client信息");
			}
			String[] tokens = RequestUtil.extractAndDecodeHeader(header);
			assert tokens.length == 2;

			String clientId = tokens[0];
			String clientSecret = tokens[1];

			ClientDetails clientDetails = clientDetailsService.loadClientByClientId(clientId);

			if (clientDetails == null) {
				throw new UnapprovedClientAuthenticationException("clientId对应的配置信息不存在:" + clientId);
			} else if (!StringUtils.equals(clientDetails.getClientSecret(), clientSecret)) {
				throw new UnapprovedClientAuthenticationException("clientSecret不匹配:" + clientId);
			}
			token = uacUserTokenService.refreshToken(access_token,refresh_token, request);
		} catch (Exception e) {
			logger.error("refreshToken={}", e.getMessage(), e);
			return WrapMapper.error();
		}
		return WrapMapper.ok(token);
	}
}
