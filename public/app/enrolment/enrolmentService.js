(function () {
    'use strict';
    angular.module('app.enrolment')
        .service('enrolmentService', ['$translate', '$q', '$location', '$uibModal', 'dialogs', 'EnrollRes', 'SettingsResource',
            'StudentExamRes', 'EXAM_CONF',
            function ($translate, $q, $location, $modal, dialogs, EnrollRes, SettingsResource, StudentExamRes, EXAM_CONF) {

                var self = this;

                var setMaturityInstructions = function (exam) {
                    var deferred = $q.defer();
                    if (exam.examLanguages.length !== 1) {
                        console.warn('Exam has no exam languages or it has several!');
                    }
                    var lang = exam.examLanguages.length > 0 ? exam.examLanguages[0].code : 'fi';
                    SettingsResource.maturityInstructions.get({lang: lang}, function (data) {
                        exam.maturityInstructions = data.value;
                        return deferred.resolve(exam);
                    });
                    return deferred.promise;
                };

                self.enroll = function (exam) {
                    var deferred = $q.defer();
                    EnrollRes.enroll.create({code: exam.course.code, id: exam.id},
                        function () {
                            toastr.success($translate.instant('sitnet_you_have_enrolled_to_exam') + '<br/>' +
                                $translate.instant('sitnet_remember_exam_machine_reservation'));
                            $location.path('/calendar/' + exam.id);
                            deferred.resolve();
                        },
                        function (error) {
                            toastr.error(error.data);
                            deferred.reject(error);
                        });
                    return deferred.promise;
                };

                self.checkAndEnroll = function (exam) {
                    EnrollRes.check.get({id: exam.id}, function () {
                            // already enrolled
                            toastr.error($translate.instant('sitnet_already_enrolled'));
                        }, function (err) {
                            if (err.status === 403) {
                                toastr.error(err.data);
                            }
                            if (err.status === 404) {
                                self.enroll(exam);
                            }
                        }
                    );
                };

                self.enrollStudent = function (exam, student) {
                    var deferred = $q.defer();
                    var data = {eid: exam.id};
                    if (student.id) {
                        data.uid = student.id;
                    } else {
                        data.email = student.email;
                    }
                    EnrollRes.enrollStudent.create(data,
                        function (enrolment) {
                            toastr.success($translate.instant('sitnet_student_enrolled_to_exam'));
                            deferred.resolve(enrolment);
                        },
                        function (error) {
                            deferred.reject(error);
                        });
                    return deferred.promise;
                };

                self.listEnrolments = function (scope, code, id) {

                    // JSa 29.8. Removed ..else -> need both exam and exams for the new view

                    if (id) {
                        EnrollRes.enroll.get({code: code, id: id},
                            function (exam) {
                                exam.languages = exam.examLanguages.map(function (lang) {
                                    return getLanguageNativeName(lang.code);
                                });
                                setMaturityInstructions(exam).then(function (data) {
                                    exam = data;
                                    EnrollRes.check.get({id: exam.id}, function (enrolments) {
                                        exam.alreadyEnrolled = true;
                                        enrolments.forEach(function (enrolment) {
                                            if (enrolment.reservation) {
                                                exam.reservationMade = true;
                                            }
                                        });

                                        scope.exam = exam;
                                    }, function (err) {
                                        exam.alreadyEnrolled = err.status !== 404;
                                        if (err.status === 403) {
                                            exam.noTrialsLeft = true;
                                        }

                                        exam.reservationMade = false;
                                        scope.exam = exam;


                                    });
                                });
                            },
                            function (error) {
                                toastr.error(error.data);
                            });
                    }
                    EnrollRes.list.get({code: code},
                        function (exams) {
                            scope.exams = exams.map(function (exam) {
                                exam.languages = exam.examLanguages.map(function (lang) {
                                    return getLanguageNativeName(lang.code);
                                });
                                return exam;
                            });

                            // remove duplicate exam, which is already shown at the detailed info section.
                            if (id) {
                                angular.forEach(scope.exams, function (value, key) {
                                    if (value.id == id) {
                                        scope.exams.splice(scope.exams.indexOf(value), 1);
                                    }
                                });
                            }

                            checkEnrolment(scope.exams);

                        },
                        function (error) {
                            toastr.error(error.data);
                        });
                };

                var checkEnrolment = function (exams) {

                    exams.forEach(function (exam) {

                        EnrollRes.check.get({id: exam.id}, function (enrolments) {
                                // check if student has reserved aquarium
                                enrolments.forEach(function (enrolment) {
                                    if (enrolment.reservation) {
                                        exam.reservationMade = true;
                                    }
                                });
                                // enrolled to exam
                                exam.enrolled = true;
                            }, function (err) {
                                // not enrolled or made reservations
                                exam.enrolled = false;
                                exam.reservationMade = false;
                            }
                        );

                    });

                };

                self.removeEnrolment = function (enrolment, enrolments) {
                    if (enrolment.reservation) {
                        toastr.error($translate.instant('sitnet_cancel_reservation_first'));
                    } else {
                        dialogs.confirm($translate.instant('sitnet_confirm'),
                            $translate.instant('sitnet_are_you_sure')).result
                            .then(function () {
                                EnrollRes.enrolment.remove({id: enrolment.id}, function () {
                                    enrolments.splice(enrolments.indexOf(enrolment), 1);
                                });
                            });
                    }
                };

                self.gotoList = function (code) {
                    $location.path('enroll/' + code);
                };

                self.addEnrolmentInformation = function (enrolment) {
                    var modalController = ['$scope', '$uibModalInstance', function ($scope, $modalInstance) {
                        $scope.enrolment = angular.copy(enrolment);
                        $scope.ok = function () {
                            $modalInstance.close('Accepted');
                            enrolment.information = $scope.enrolment.information;
                            StudentExamRes.enrolment.update({
                                eid: enrolment.id,
                                information: $scope.enrolment.information
                            }, function () {
                                toastr.success($translate.instant('sitnet_saved'));
                            });
                        };

                        $scope.cancel = function () {
                            $modalInstance.close('Canceled');
                        };
                    }];

                    var modalInstance = $modal.open({
                        templateUrl: EXAM_CONF.TEMPLATES_PATH + 'enrolment/add_enrolment_information.html',
                        backdrop: 'static',
                        keyboard: true,
                        controller: modalController,
                        resolve: {
                            enrolment: function () {
                                return enrolment;
                            }
                        }
                    });

                    modalInstance.result.then(function () {
                        console.log('closed');
                    });
                };

                self.showInstructions = function (enrolment) {
                    var modalController = ['$scope', '$uibModalInstance', function ($scope, $modalInstance) {
                        $scope.title = 'sitnet_instruction';
                        $scope.instructions = enrolment.exam.enrollInstruction;
                        $scope.ok = function () {
                            $modalInstance.close('Accepted');
                        };
                    }];

                    var modalInstance = $modal.open({
                        templateUrl: EXAM_CONF.TEMPLATES_PATH + 'reservation/show_instructions.html',
                        backdrop: 'static',
                        keyboard: true,
                        controller: modalController,
                        resolve: {
                            instructions: function () {
                                return enrolment.exam.enrollInstruction;
                            }
                        }
                    });

                    modalInstance.result.then(function () {
                        console.log('closed');
                    });
                };

                self.showMaturityInstructions = function (enrolment) {
                    var modalController = ['$scope', '$uibModalInstance', 'SettingsResource', function ($scope, $modalInstance, SettingsResource) {
                        if (enrolment.exam.examLanguages.length !== 1) {
                            console.warn('Exam has no exam languages or it has several!');
                        }
                        var lang = enrolment.exam.examLanguages && enrolment.exam.examLanguages.length > 0
                            ? enrolment.exam.examLanguages[0].code : 'fi';
                        $scope.title = 'sitnet_maturity_instructions';
                        SettingsResource.maturityInstructions.get({lang: lang}, function (data) {
                            $scope.instructions = data.value;
                        });
                        $scope.ok = function () {
                            $modalInstance.close('Accepted');
                        };
                    }];

                    var modalInstance = $modal.open({
                        templateUrl: EXAM_CONF.TEMPLATES_PATH + 'reservation/show_instructions.html',
                        backdrop: 'static',
                        keyboard: true,
                        controller: modalController,
                        resolve: {
                            instructions: function () {
                                return enrolment.exam.enrollInstruction;
                            }
                        }
                    });

                    modalInstance.result.then(function () {
                        console.log('closed');
                    });
                };


            }]);
}());
