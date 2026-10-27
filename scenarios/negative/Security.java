class Security { void configure(HttpSecurity http) { http.authorizeHttpRequests(a -> a.anyRequest().authenticated()); } }
