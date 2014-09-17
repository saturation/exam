(function () {
    'use strict';
    angular.module("sitnet.controllers")
        .controller('StudentExamController', ['$rootScope', '$scope', '$interval', '$routeParams', '$http', '$modal', '$location', '$translate', '$timeout', 'SITNET_CONF', 'StudentExamRes', 'QuestionRes',
            function ($rootScope, $scope, $interval, $routeParams, $http, $modal, $location, $translate, $timeout, SITNET_CONF, StudentExamRes, QuestionRes) {

                $scope.sectionsBar = SITNET_CONF.TEMPLATES_PATH + "student/student_sections_bar.html";
                $scope.multipleChoiseOptionTemplate = SITNET_CONF.TEMPLATES_PATH + "student/multiple_choice_option.html";
                $scope.essayQuestionTemplate = SITNET_CONF.TEMPLATES_PATH + "student/essay_question.html";
                $scope.sectionTemplate = SITNET_CONF.TEMPLATES_PATH + "student/section_template.html";

//                $scope.exams = StudentExamRes.exams.query();
                $scope.tempQuestion = null;

//                $scope.autoSaver = $interval(function(){
//
//                    find all Essay questions
//                    loop and save all Essay answers
//
//
//                }, 10000)

                // section back / forward buttons
                $scope.guide = false;
                $scope.switchToGuide = function(b) {
                    $scope.guide = b;
                };
                $scope.previousButton = false;
                $scope.previousButtonText = "";

                $scope.nextButton = false;
                $scope.nextButtonText = "";

                $scope.switchButtons = function(section) {

                    var i = section.index - 1;

                    if ($scope.doexam.examSections[i-1]) {
                        $scope.previousButton = true;
                        $scope.previousButtonText = $scope.doexam.examSections[i-1].index + ". " + $scope.doexam.examSections[i-1].name;
                    } else {
                        $scope.previousButton = true;
                        $scope.previousButtonText = $translate("sitnet_exam_quide");
                    }

                    if(i == 0 && $scope.guide) {
                        $scope.nextButton = true;
                        $scope.nextButtonText = $scope.doexam.examSections[0].index + ". " + $scope.doexam.examSections[0].name;
                    } else if (!$scope.guide && $scope.doexam.examSections[i+1]) {
                        $scope.nextButton = true;
                        $scope.nextButtonText = $scope.doexam.examSections[i+1].index + ". " + $scope.doexam.examSections[i+1].name;
                    } else {
                        $scope.nextButton = false;
                        $scope.nextButtonText = "";
                    }
                };

                $scope.doExam = function (hash) {
                    $http.get('/student/doexam/' + $routeParams.hash)
                        .success(function (data, status, headers, config) {
                            $scope.doexam = data;
                            $scope.activeSection = $scope.doexam.examSections[0];

                            // set sections and question numbering
                            angular.forEach($scope.doexam.examSections, function (section, index) {
                                section.index = index +1;

                                angular.forEach(section.questions, function (question, index) {
                                    question.index = index +1;
                                });
                            });

                                // Loop through all questions in the active section
                            angular.forEach($scope.activeSection.questions, function (question, index) {
                                var template = "";
                                switch (question.type) {
                                    case "MultipleChoiceQuestion":
                                        template = $scope.multipleChoiseOptionTemplate;
                                        break;
                                    case "EssayQuestion":
                                        template = $scope.essayQuestionTemplate;
                                        break;
                                    default:
                                        template = "fa-question-circle";
                                        break;
                                }
                                question.template = template;

                                question.expanded = false;

                                $scope.setQuestionColors(question);
                            });

                            $scope.switchToGuide(true);
                            $scope.switchButtons($scope.doexam.examSections[0]);

                            $http.get('/examenrolmentroom/' + $scope.doexam.id)
                                .success(function (data, status, headers, config) {
                                    $scope.info = data;
                                    $scope.currentLanguageText = currentLanguage();
                                });
                        }).
                        error(function (data, status, headers, config) {
                            // called asynchronously if an error occurs
                            // or server returns response with an error status.
                        });
                };

                $rootScope.$on('$translateChangeSuccess', function () {
                    $scope.currentLanguageText = currentLanguage();
                });

                function currentLanguage () {
                    var tmp = "";

                    switch($translate.uses()) {
                        case "fi":
                            if($scope.info.reservation.machine.room.roomInstruction) {
                                tmp = $scope.info.reservation.machine.room.roomInstruction;
                            }
                            break;
                        case "swe":
                            if($scope.info.reservation.machine.room.roomInstructionSV) {
                                tmp = $scope.info.reservation.machine.room.roomInstructionSV;
                            }
                            break;
                        case "eng":
                            if($scope.info.reservation.machine.room.roomInstructionEN) {
                                tmp = $scope.info.reservation.machine.room.roomInstructionEN;
                            }
                            break;
                        default:
                            if($scope.info.reservation.machine.room.roomInstructionEN) {
                                tmp = $scope.info.reservation.machine.room.roomInstructionEN;
                            }
                            break;
                    }
                    return tmp;
                }


                if ($routeParams.hash != undefined) {
                    $scope.doExam();
                }

                $scope.activateExam = function (exam) {

                    $scope.doexam = exam;

                    $http.get('/student/doexam/' + $scope.doexam.hash)
                        .success(function (clonedExam) {
                            $scope.clonedExam = clonedExam;
                            $location.path('/student/doexam/' + clonedExam.hash);
                        }).
                        error(function (error) {
                            console.log('Error happened: ' + error);
                        });

//                    removed because SIT-510
//                    $scope.doexam = exam;
//                    var modalInstance = $modal.open({
//                        templateUrl: 'assets/templates/dialog_terms_of_use.html',
//                        backdrop: 'static',
//                        keyboard: true,
//                        controller: "ModalInstanceCtrl"
//                    });
//
//                    modalInstance.result.then(function () {
//                        $http.get('/student/doexam/' + $scope.doexam.hash)
//                            .success(function (clonedExam) {
//                                $scope.clonedExam = clonedExam;
//                                $location.path('/student/doexam/' + clonedExam.hash);
//                            }).
//                            error(function (error) {
//                                console.log('Error happened: ' + error);
//                            });
//                    }, function () {
//                        console.log('Modal dismissed at: ' + new Date());
//                    });
                };

                $scope.continueExam = function (exam) {
                    $http.get('/student/doexam/' + exam.hash)
                        .success(function (clonedExam) {
                            $scope.clonedExam = clonedExam;
                            $location.path('/student/doexam/' + clonedExam.hash);
                        }).
                        error(function (error) {
                            console.log('Error happened: ' + error);
                        });
                };

                $scope.setActiveSection = function (section) {
                    $scope.activeSection = section;
                    $scope.switchButtons(section);

                    // Loop through all questions in the active section
                    angular.forEach($scope.activeSection.questions, function (question, index) {

                        var template = "";
                        switch (question.type) {
                            case "MultipleChoiceQuestion":
                                template = $scope.multipleChoiseOptionTemplate;

//                                console.log("asd: "+ question.answer.option);

                                break;
                            case "EssayQuestion":
                                template = $scope.essayQuestionTemplate;
                                break;
                            default:
                                template = "fa-question-circle";
                                break;
                        }
                        question.template = template;

                        if (question.expanded == null) {
                            question.expanded = true;
                        }

                        $scope.setQuestionColors(question);
                    });
                };

                $scope.setPreviousSection = function (exam, active_section) {
//                    var sectionCount = exam.examSections.length;

                    if(!$scope.guide) {
                        // Loop through all sections in the exam
                        angular.forEach(exam.examSections, function (section, index) {
                            // If section is same as the active_section
                            if (angular.equals(section, active_section)) {
                                // If this is the first element in the array
                                if (index == 0) {
                                    $scope.switchToGuide(true);
                                    $scope.setActiveSection(exam.examSections[0]);
                                }
                                else {
                                    $scope.setActiveSection(exam.examSections[index - 1]);
                                }
                            }
                        });
                    }
                };

                $scope.setNextSection = function (exam, active_section) {
                    var sectionCount = exam.examSections.length;

                    if($scope.guide) {
                        $scope.switchToGuide(false);
                        $scope.setActiveSection(exam.examSections[0]);
                    } else {
                        // Loop through all sections in the exam
                        angular.forEach(exam.examSections, function (section, index) {
                            // If section is same as the active_section
                            if (angular.equals(section, active_section)) {
                                // If this is the last element in the array
                                if (index == sectionCount - 1) {
                                } else {
                                    $scope.setActiveSection(exam.examSections[index + 1]);
                                }
                            }
                        });
                    }
                };

                // Called when the save and exit button is clicked
                $scope.saveExam = function (doexam) {

                    if (confirm($translate('sitnet_confirm_turn_exam'))) {

                        StudentExamRes.exams.update({id: doexam.id}, function () {

                            // Todo: tässä vaiheessa pitäisi tehdä paljon muitakin tarkistuksia


                            toastr.info("Tentti palautettu");
                            $location.path("/home/");

                        }, function () {
                            toastr.error(error.data);
                        });
                    }
                };

                // Called when the abort button is clicked
                $scope.abortExam = function (doexam) {

                    if (confirm($translate('sitnet_confirm_abort_exam'))) {
                        StudentExamRes.exam.abort({id: doexam.id}, {data: doexam}, function () {
                            toastr.info("Tentti keskeytetty.");
                            $location.path("/home/");

                        }, function () {
                            toastr.error(error.data);
                        });
                    }
                };

                // Called when a radiobutton is selected
                $scope.radioChecked = function (doexam, question, option) {
                    question.answered = true;
                    question.questionStatus = $translate("sitnet_question_answered");

                    StudentExamRes.multipleChoiseAnswer.saveMultipleChoice({hash: doexam.hash, qid: question.id, oid: option.id},
                        function (updated_answer) {
                        question.answer = updated_answer;
                        toastr.info("Vastaus lisätty kysymykseen.");
                    }, function () {
                        toastr.error(error.data);
                    });
                };

                $scope.saveEssay = function (question, answer) {
                	question.answered = true;
                	question.questionStatus = $translate("sitnet_question_answered");

                    var params = {
                        hash: $scope.doexam.hash,
                        qid: question.id
                    };
                    var msg = {};
                    msg.answer = answer;
                    StudentExamRes.essayAnswer.saveEssay(params, msg, function () {
                        toastr.info("Vastaus lisätty kysymykseen.");
                    }, function () {
                        toastr.error(error.data);
                    });
                };

                $scope.formatRemainingTime = function(time){

                    var remaining = 0;
                    var minutes = time / 60;
                    if(minutes > 1) {
                        minutes = (minutes|0);
                        var seconds = time - ( minutes * 60 );
                        remaining = minutes + "m " + seconds + 's';
                    } else {
                        remaining = time + 's';
                    }

                    return remaining;

                };

                $scope.remainingTime = '';
                var updateCheck = 30;
                var updateInterval = 0;
                var count = function () {
                    updateInterval++;
                    $timeout(count, 1000);
                    if (updateInterval >= updateCheck) {
                        updateInterval = 0;
                        var req = $http.get('/time/' + $scope.doexam.id);
                        req.success(function (reply) {
                            $scope.remainingTime = reply;
                        });
                    } else {
                        $scope.remainingTime--;
                        return;
                    }
                };

                // Called when the chevron is clicked
                $scope.chevronClicked = function (question) {

                    if (question.type == "EssayQuestion") {

                    }
                    $scope.setQuestionColors(question);
                };

                $scope.isAnswer = function (question, option) {

                    if(question.answer === null)
                        return false;
                    else if(question.answer.option === null)
                        return false;
                    else if(option.option === question.answer.option.option)
                        return true;
                };

                $scope.setQuestionColors = function(question) {
                    // State machine for resolving how the question header is drawn
                    if (question.answered) {

                        question.questionStatus = $translate("sitnet_question_answered");

                        if (question.expanded) {
                            question.selectedAnsweredState = 'question-active-header';
                        } else {
                            question.selectedAnsweredState = 'question-answered-header';
                        }
                    } else {

                        question.questionStatus = $translate("sitnet_question_unanswered");

                        if (question.expanded) {
                            question.selectedAnsweredState = 'question-active-header';
                        } else {
                            question.selectedAnsweredState = 'question-unanswered-header';
                        }
                    }
                };
            }]);
}());