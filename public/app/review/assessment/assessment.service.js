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

'use strict';
angular.module('app.review')
    .service('Assessment', ['$q', '$resource', '$translate', '$location', '$timeout', 'dialogs', 'ExamRes', 'Session', 'Question', 'toast',
        function ($q, $resource, $translate, $location, $timeout, dialogs, ExamRes, Session, Question, toast) {

            var self = this;

            self.noShowApi = $resource('/app/noshows/:eid/:uid', {
                eid: '@eid',
                uid: '@uid'
            });

            self.participationsApi = $resource('/app/examparticipations/:eid/:uid', {
                eid: '@eid',
                uid: '@uid'
            });

            self.examAssessmentApi = $resource('/app/review/:id/info', {
                id: '@id'
            }, {
                update: {method: 'PUT'}
            });

            self.saveFeedback = function (exam, silent) {
                var deferred = $q.defer();
                var examFeedback = {
                    'comment': exam.examFeedback.comment
                };

                // TODO: combine these to one API call!
                // Update comment
                if (exam.examFeedback.id) {
                    ExamRes.comment.update({
                        eid: exam.id,
                        cid: exam.examFeedback.id
                    }, examFeedback, function (data) {
                        if (!silent) {
                            toast.info($translate.instant('sitnet_comment_updated'));
                        }
                        deferred.resolve();
                    }, function (error) {
                        toast.error(error.data);
                        deferred.reject();
                    });
                    // Insert new comment
                } else {
                    ExamRes.comment.insert({
                        eid: exam.id,
                        cid: 0
                    }, examFeedback, function (comment) {
                        if (!silent) {
                            toast.info($translate.instant('sitnet_comment_added'));
                        }
                        exam.examFeedback = comment;
                        deferred.resolve();
                    }, function (error) {
                        toast.error(error.data);
                        deferred.reject();
                    });
                }
                return deferred.promise;
            };

            self.isReadOnly = function (exam) {
                return exam && ['GRADED_LOGGED', 'ARCHIVED', 'ABORTED', 'REJECTED'].indexOf(exam.state) > -1;
            };

            self.isGraded = function (exam) {
                return exam && exam.state === 'GRADED';
            };

            self.pickExamLanguage = function (exam) {
                var lang = exam.answerLanguage;
                if (lang) {
                    return {code: lang};
                }
                else if (exam.examLanguages.length === 1) {
                    lang = exam.examLanguages[0];
                }
                return lang;
            };

            self.checkCredit = function (exam, silent) {
                var credit = exam.customCredit;
                var valid = !isNaN(credit) && credit >= 0;
                if (!valid) {
                    if (!silent) {
                        toast.error($translate.instant('sitnet_not_a_valid_custom_credit'));
                    }
                    // Reset to default
                    exam.customCredit = exam.course.credits;
                }
                return valid;
            };

            // Defining markup outside templates is not advisable, but creating a working custom dialog template for this
            // proved to be a bit too much of a hassle. Lets live with this.
            self.getRecordReviewConfirmationDialogContent = function (feedback) {
                return '<h4>' + $translate.instant('sitnet_teachers_comment') + '</h4>' +
                    feedback + '<br/><strong>' + $translate.instant('sitnet_confirm_record_review') + '</strong>';
            };

            self.countCharacters = function (text) {
                if (!text) {
                    return 0;
                }
                var normalizedText = text
                    .replace(/\s/g, '')
                    .replace(/&nbsp;/g, '')
                    .replace(/(\r\n|\n|\r)/gm, '')
                    .replace(/&nbsp;/gi, ' ');
                normalizedText = strip(normalizedText)
                    .replace(/^([\t\r\n]*)$/, '');
                return normalizedText.length;
            };

            self.countWords = function (text) {
                if (!text) {
                    return 0;
                }
                var normalizedText = text
                    .replace(/(\r\n|\n|\r)/gm, ' ')
                    .replace(/^\s+|\s+$/g, '')
                    .replace('&nbsp;', ' ');

                normalizedText = strip(normalizedText);

                var words = normalizedText.split(/\s+/);

                for (var wordIndex = words.length - 1; wordIndex >= 0; wordIndex--) {
                    if (words[wordIndex].match(/^([\s\t\r\n]*)$/)) {
                        words.splice(wordIndex, 1);
                    }
                }
                return words.length;
            };

            self.getExitUrl = function (exam) {
                var user = Session.getUser || {isAdmin: false};
                return user.isAdmin ? '/' : '/exams/' + exam.parent.id + '/4';
            };

            self.createExamRecord = function (exam, needsConfirmation, followUpUrl) {

                if (!self.checkCredit(exam)) {
                    return;
                }
                var messages = getErrors(exam);
                if (messages.length > 0) {
                    messages.forEach(function (msg) {
                        toast.error($translate.instant(msg));
                    });
                }
                else {
                    var dialogNote, res;
                    if (exam.gradeless) {
                        dialogNote = $translate.instant('sitnet_confirm_archiving_without_grade');
                        res = ExamRes.register.add;
                    } else {
                        dialogNote = self.getRecordReviewConfirmationDialogContent(exam.examFeedback.comment);
                        res = ExamRes.saveRecord.add;
                    }
                    var payload = getPayload(exam, 'GRADED');
                    if (needsConfirmation) {
                        var dialog = dialogs.confirm($translate.instant('sitnet_confirm'), dialogNote);
                        dialog.result.then(function () {
                            register(exam, res, payload, followUpUrl);
                        });
                    } else {
                        sendToRegistry(payload, res, exam, followUpUrl);
                    }
                }
            };

            self.saveEssayScore = function (question) {
                if (!question.essayAnswer || isNaN(question.essayAnswer.evaluatedScore)) {
                    return $q.reject();
                }

                return Question.essayScoreApi.update({
                    id: question.id,
                    evaluatedScore: question.essayAnswer.evaluatedScore
                }).$promise;
            };

            self.saveAssessmentInfo = function (exam) {
                if (exam.state === 'GRADED_LOGGED') {
                    self.examAssessmentApi.update({id: exam.id, assessmentInfo: exam.assessmentInfo}, function () {
                        toast.info($translate.instant('sitnet_saved'));
                    });
                }
            };

            self.saveAssessment = function (exam, modifiable) {
                if (!modifiable) {
                    if (exam.state !== 'GRADED') {
                        // Just save feedback and leave
                        saveFeedback(exam).then(function () {
                            toast.info($translate.instant('sitnet_saved'));
                            $location.path('exams/' + exam.parent.id + '/4');
                        });
                    }
                }
                else {
                    if (!self.checkCredit(exam)) {
                        return;
                    }
                    var messages = getErrors(exam);
                    if (exam.executionType.type === 'MATURITY') {
                        sendAssessment(exam.state, getPayload(exam), messages, exam);
                    } else {
                        var oldState = exam.state;
                        var newState = messages.length > 0 ? 'REVIEW_STARTED' : 'GRADED';
                        var payload = getPayload(exam, newState);
                        if (newState !== 'GRADED' || oldState === 'GRADED') {
                            sendAssessment(newState, payload, messages, exam);
                        } else {
                            var dialog = dialogs.confirm($translate.instant('sitnet_confirm'), $translate.instant('sitnet_confirm_grade_review'));
                            dialog.result.then(function () {
                                sendAssessment(newState, payload, messages, exam);
                            });
                        }
                    }
                }
            };

            self.rejectMaturity = function (exam, askConfirmation, followUpUrl) {
                var reject = function () {
                    saveFeedback(exam).then(function () {
                        var payload = getPayload(exam, 'REJECTED');
                        ExamRes.review.update(payload, function () {
                            toast.info($translate.instant('sitnet_maturity_rejected'));
                            $location.path(followUpUrl || self.getExitUrl(exam));
                        }, function (error) {
                            toast.error(error.data);
                        });
                    });
                };
                if (askConfirmation) {
                    var dialog = dialogs.confirm($translate.instant('sitnet_confirm'),
                        $translate.instant('sitnet_confirm_maturity_disapproval'));
                    dialog.result.then(function () {
                        reject();
                    });
                } else {
                    reject();
                }

            };

            self.getPayload = function (exam, state) {
                return getPayload(exam, state);
            };

            var strip = function (html) {
                var tmp = document.createElement('div');
                tmp.innerHTML = html;

                if (!tmp.textContent && typeof tmp.innerText === 'undefined') {
                    return '';
                }

                return tmp.textContent || tmp.innerText;
            };

            var sendAssessment = function (newState, payload, messages, exam) {
                ExamRes.review.update(payload, function () {
                    saveFeedback(exam).then(function () {
                        if (newState === 'REVIEW_STARTED') {
                            messages.forEach(function (msg) {
                                toast.warning($translate.instant(msg));
                            });
                            $timeout(function () {
                                toast.info($translate.instant('sitnet_review_saved'));
                            }, 1000);
                        } else {
                            toast.info($translate.instant('sitnet_review_graded'));
                            $location.path(self.getExitUrl(exam));
                        }
                    });
                }, function (error) {
                    toast.error(error.data);
                });
            };

            var getErrors = function (exam) {
                var messages = [];
                if (!exam.grade.id) {
                    messages.push('sitnet_participation_unreviewed');
                }
                if (!exam.creditType.type) {
                    messages.push('sitnet_exam_choose_credit_type');
                }
                if (!exam.answerLanguage) {
                    messages.push('sitnet_exam_choose_response_language');
                }
                return messages;
            };

            var saveFeedback = function (exam) {
                return self.saveFeedback(exam, true);
            };

            var sendToRegistry = function (payload, res, exam, followUpUrl) {
                payload.state = 'GRADED_LOGGED';
                res(payload, function () {
                    toast.info($translate.instant('sitnet_review_recorded'));
                    $location.path(followUpUrl || self.getExitUrl(exam));
                }, function (error) {
                    toast.error(error.data);
                });
            };

            var getPayload = function (exam, state) {
                return {
                    'id': exam.id,
                    'state': state || exam.state,
                    'grade': exam.grade && exam.grade.id ? exam.grade.id : undefined,
                    'gradeless': exam.gradeless,
                    'customCredit': exam.customCredit,
                    'creditType': exam.creditType ? exam.creditType.type : undefined,
                    'answerLanguage': exam.answerLanguage ? exam.answerLanguage.code : undefined,
                    'additionalInfo': exam.additionalInfo
                };
            };

            var register = function (exam, res, payload, followUpUrl) {
                saveFeedback(exam).then(function () {
                    ExamRes.review.update(payload, function () {
                        if (exam.state !== 'GRADED') {
                            toast.info($translate.instant('sitnet_review_graded'));
                        }
                        sendToRegistry(payload, res, exam, followUpUrl);
                    }, function (error) {
                        toast.error(error.data);
                    });
                });
            };


        }]);

