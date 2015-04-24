(function () {
    'use strict';
    angular.module('sitnet.services')
        .factory('sessionService', ['$q', '$sessionStorage', '$translate', '$injector', 'tmhDynamicLocale', 'SITNET_CONF',
            function ($q, $sessionStorage, $translate, $injector, tmhDynamicLocale, SITNET_CONF) {

                var _user;
                var _pending;
                var getUser = function () {
                    return _user;
                };

                var getUserName = function () {
                    if (_user) {
                        return _user.firstname + " " + _user.lastname;
                    }
                };

                var setUser = function (user) {
                    _user = user;
                };

                // Service needs to be accessed like this because of circular dependency issue
                var $http;
                var http = function () {
                    return $http = $http || $injector.get('$http');
                };

                var hasRole = function (user, role) {
                    if (!user || !user.roles) {
                        return false;
                    }
                    return user.roles.some(function (r) {
                        return (r.name === role)
                    });
                };

                var isLoggingIn = function () {
                    return _pending === true;
                };

                var logout = function () {
                    var deferred = $q.defer();
                    http().post('/logout').success(function (data) {
                        delete $sessionStorage[SITNET_CONF.AUTH_STORAGE_KEY];
                        delete http().defaults.headers.common;
                        _user = undefined;
                        deferred.resolve(data);
                    }).error(function (error) {
                        toastr(error.data);
                        deferred.reject();
                    });
                    return deferred.promise;
                };

                var translate = function (lang) {
                    $translate.uses(lang);
                    tmhDynamicLocale.set(lang);
                };

                var login = function (username, password) {
                    var deferred = $q.defer();
                    var credentials = {
                        username: username,
                        password: password
                    };
                    http().post('/login', credentials, {ignoreAuthModule: true}).success(
                        function (user) {
                            var header = {};
                            header[SITNET_CONF.AUTH_HEADER] = user.token;
                            http().defaults.headers.common = header;

                            _user = {
                                id: user.id,
                                firstname: user.firstname,
                                lastname: user.lastname,
                                lang: user.lang,
                                isAdmin: (hasRole(user, 'ADMIN')),
                                isStudent: (hasRole(user, 'STUDENT')),
                                isTeacher: (hasRole(user, 'TEACHER')),
                                isLoggedOut: false,
                                token: user.token,
                                hasAcceptedUserAgreament: user.hasAcceptedUserAgreament
                            };

                            $sessionStorage[SITNET_CONF.AUTH_STORAGE_KEY] = _user;
                            _pending = false;
                            translate(_user.lang);
                            deferred.resolve();
                        }).error(function (error) {
                            _pending = false;
                            deferred.reject(error);
                        });
                    _pending = true;
                    return deferred.promise;
                };

                var switchLanguage = function (lang) {
                    if (!_user) {
                        translate(lang);
                    } else {
                        http().put('/user/lang', {lang: lang}).success(function () {
                            _user.lang = lang;
                            translate(lang);
                        }).error(function () {
                            toastr.error('failed to switch language');
                        })
                    }
                };

                return {
                    login: login,
                    logout: logout,
                    isLoggingIn: isLoggingIn,
                    getUser: getUser,
                    getUserName: getUserName,
                    setUser: setUser,
                    switchLanguage: switchLanguage,
                    translate: translate
                };

            }]);
}());
