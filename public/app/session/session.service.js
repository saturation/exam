/*
 * Copyright (c) 2017 The members of the EXAM Consortium (https://confluence.csc.fi/display/EXAM/Konsortio-organisaatio)
 *
 * Licensed under the EUPL, Version 1.1 or - as soon they will be approved by the European Commission - subsequent
 * versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/software/page/eupl/licence-eupl
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the Licence is distributed
 * on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and limitations under the Licence.
 */

'';
'use strict';
angular.module('app.session')
    .service('Session', ['$q', '$interval', '$sessionStorage', '$translate', '$injector', '$location',
        '$rootScope', '$timeout', 'tmhDynamicLocale', 'EXAM_CONF', 'toast',
        function ($q, $interval, $sessionStorage, $translate, $injector, $location, $rootScope, $timeout,
                  tmhDynamicLocale, EXAM_CONF, toast) {

            var self = this;

            var PING_INTERVAL = 60 * 1000;

            var _user;
            var _env;
            var _scheduler;

            // Services need to be accessed like this because of circular dependency issues
            var $http;
            var http = function () {
                $http = $http || $injector.get('$http');
                return $http;
            };
            var $modal;
            var modal = function () {
                $modal = $modal || $injector.get('$uibModal');
                return $modal;
            };
            var $route;
            var route = function () {
                $route = $route || $injector.get('$route');
                return $route;
            };
            var UserRes;
            var userRes = function () {
                UserRes = UserRes || $injector.get('UserRes');
                return UserRes;
            };

            self.getUser = function () {
                return _user;
            };

            self.getUserName = function () {
                if (_user) {
                    return _user.firstName + ' ' + _user.lastName;
                }
            };

            self.setUser = function (user) {
                _user = user;
            };

            var hasRole = function (user, role) {
                if (!user || !user.loginRole) {
                    return false;
                }
                return user.loginRole.name === role;
            };

            var init = function () {
                var deferred = $q.defer();
                if (!_env) {
                    http().get('/app/settings/environment').success(function (data) {
                        _env = data;
                        deferred.resolve();
                    });
                } else {
                    deferred.resolve();
                }
                return deferred.promise;
            };

            self.setLoginEnv = function (scope) {
                init().then(function () {
                    if (!_env.isProd) {
                        scope.loginTemplatePath = EXAM_CONF.TEMPLATES_PATH + 'session/templates/dev_login.html';
                    }
                });
            };

            var hasPermission = function (user, permission) {
                if (!user) {
                    return false;
                }
                return user.permissions.some(function (p) {
                    return p.type === permission;
                });
            };

            var onLogoutSuccess = function (data) {
                $rootScope.$broadcast('userUpdated');
                toast.success($translate.instant('sitnet_logout_success'));
                window.onbeforeunload = null;
                var localLogout = window.location.protocol + '//' + window.location.host + '/Shibboleth.sso/Logout';
                if (data && data.logoutUrl) {
                    window.location.href = data.logoutUrl + '?return=' + localLogout;
                } else if (!_env || _env.isProd) {
                    // redirect to SP-logout directly
                    window.location.href = localLogout;
                } else {
                    // DEV logout
                    $location.path('/');
                    $rootScope.$broadcast('devLogout');
                }
                $timeout(toast.clear, 300);
            };

            self.logout = function () {
                if (!_user) {
                    return;
                }
                http().post('/app/logout').success(function (data) {
                    delete $sessionStorage[EXAM_CONF.AUTH_STORAGE_KEY];
                    delete http().defaults.headers.common;
                    _user = undefined;
                    onLogoutSuccess(data);
                }).error(function (error) {
                    toast.error(error.data);
                });
            };

            self.translate = function (lang) {
                $translate.use(lang);
                tmhDynamicLocale.set(lang);
            };

            self.openEulaModal = function (user) {
                modal().open({
                    backdrop: 'static',
                    keyboard: true,
                    component: 'eula'
                }).result.then(function () {
                    userRes().updateAgreementAccepted.update(function () {
                        user.userAgreementAccepted = true;
                        self.setUser(user);
                        if ($location.url() === '/login' || $location.url() === '/logout') {
                            $location.path('/');
                        } else {
                            route().reload();
                        }
                    }, function (error) {
                        toast.error(error.data);
                    });

                }, function () {
                    $location.path('/logout');
                });
            };

            self.openRoleSelectModal = function (user) {
                var ctrl = ['$scope', '$uibModalInstance', function ($scope, $modalInstance) {
                    $scope.user = user;
                    $scope.ok = function (role) {
                        userRes().userRoles.update({id: user.id, role: role.name}, function () {
                            user.loginRole = role;
                            user.isAdmin = hasRole(user, 'ADMIN');
                            user.isTeacher = hasRole(user, 'TEACHER');
                            user.isStudent = hasRole(user, 'STUDENT');
                            user.isLanguageInspector = user.isTeacher && hasPermission(user, 'CAN_INSPECT_LANGUAGE');
                            self.setUser(user);
                            $modalInstance.dismiss();
                            $rootScope.$broadcast('userUpdated');
                            if (user.isStudent && !user.userAgreementAccepted) {
                                self.openEulaModal(user);
                            } else if ($location.url() === '/login' || $location.url() === '/logout') {
                                $location.path('/');
                            } else {
                                route().reload();
                            }
                        }, function (error) {
                            toast.error(error.data);
                            $location.path('/logout');
                        });

                    };
                    $scope.cancel = function () {
                        $modalInstance.dismiss('cancel');
                        $location.path('/logout');
                    };
                }];
                var m = modal().open({
                    templateUrl: EXAM_CONF.TEMPLATES_PATH + 'session/templates/select_role.html',
                    backdrop: 'static',
                    keyboard: false,
                    controller: ctrl,
                    resolve: {
                        user: function () {
                            return user;
                        }
                    }
                });

                m.result.then(function () {
                    console.log('closed');
                });
            };

            var redirect = function () {
                if ($location.path() === '/' && _user.isLanguageInspector) {
                    $location.path('/inspections');
                } else if (_env && !_env.isProd) {
                    $location.path(_user.isLanguageInspector ? '/inspections' : '/');
                }
            };

            var onLoginSuccess = function () {
                self.restartSessionCheck();
                $rootScope.$broadcast('userUpdated');
                var welcome = function () {
                    toast.options.positionClass = 'toast-top-center';
                    toast.success($translate.instant('sitnet_welcome') + ' ' + _user.firstName + ' ' + _user.lastName);
                    $timeout(function () {
                        toast.options.positionClass = 'toast-top-right';
                    }, 2500)
                };
                $timeout(welcome, 2000);
                if (!_user.loginRole) {
                    self.openRoleSelectModal(_user);
                } else if (_user.isStudent && !_user.userAgreementAccepted) {
                    self.openEulaModal(_user);
                } else {
                    redirect();
                    route().reload();
                }
            };

            var onLoginFailure = function (message) {
                $location.path('/');
                toast.error(message);
            };

            var processLoggedInUser = function (user) {
                var header = {};
                header[EXAM_CONF.AUTH_HEADER] = user.token;
                http().defaults.headers.common = header;
                user.roles.forEach(function (role) {
                    switch (role.name) {
                        case 'ADMIN':
                            role.displayName = 'sitnet_admin';
                            role.icon = 'fa-cog';
                            break;
                        case 'TEACHER':
                            role.displayName = 'sitnet_teacher';
                            role.icon = 'fa-university';
                            break;
                        case 'STUDENT':
                            role.displayName = 'sitnet_student';
                            role.icon = 'fa-graduation-cap';
                            break;
                    }
                });

                _user = {
                    id: user.id,
                    firstName: user.firstName,
                    lastName: user.lastName,
                    lang: user.lang,
                    loginRole: user.roles.length === 1 ? user.roles[0] : undefined,
                    roles: user.roles,
                    isLoggedOut: false,
                    token: user.token,
                    userAgreementAccepted: user.userAgreementAccepted,
                    userIdentifier: user.userIdentifier,
                    permissions: user.permissions
                };
                _user.isAdmin = hasRole(_user, 'ADMIN');
                _user.isStudent = hasRole(_user, 'STUDENT');
                _user.isTeacher = hasRole(_user, 'TEACHER');
                _user.isLanguageInspector = _user.isTeacher && hasPermission(user, 'CAN_INSPECT_LANGUAGE');

                $sessionStorage[EXAM_CONF.AUTH_STORAGE_KEY] = _user;
                self.translate(_user.lang);
            };

            self.login = function (username, password) {
                var credentials = {
                    username: username,
                    password: password
                };
                var deferred = $q.defer();
                http().post('/app/login', credentials, {ignoreAuthModule: true}).then(
                    function (user) {
                        processLoggedInUser(user.data);
                        onLoginSuccess();
                        deferred.resolve(_user);
                    }, function (error) {
                        onLoginFailure(error.data);
                        deferred.reject();
                    });
                return deferred.promise;
            };

            self.switchLanguage = function (lang) {
                if (!_user) {
                    self.translate(lang);
                } else {
                    http().put('/app/user/lang', {lang: lang}).success(function () {
                        _user.lang = lang;
                        self.translate(lang);
                    }).error(function () {
                        toast.error('failed to switch language');
                    });
                }
            };

            var checkSession = function () {
                http().get('/app/checkSession').success(function (data) {
                    if (data === 'alarm') {
                        toast.options = {
                            timeOut: 0,
                            preventDuplicates: true,
                            onclick: function () {
                                http().put('/app/extendSession', {}).success(function () {
                                    toast.info($translate.instant('sitnet_session_extended'));
                                    toast.options.timeout = 1000;
                                });
                            }
                        };
                        toast.warning($translate.instant('sitnet_continue_session'),
                            $translate.instant('sitnet_session_will_expire_soon'));
                    } else if (data === 'no_session') {
                        if (_scheduler) {
                            $interval.cancel(_scheduler);
                        }
                        self.logout();
                    }
                });
            };

            self.restartSessionCheck = function () {
                if (_scheduler) {
                    $interval.cancel(_scheduler);
                }
                _scheduler = $interval(checkSession, PING_INTERVAL);
            };

            $rootScope.$on('$destroy', function () {
                if (_scheduler) {
                    $interval.cancel(_scheduler);
                }
            });

        }
    ]);

