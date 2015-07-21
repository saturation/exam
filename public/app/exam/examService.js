(function () {
    'use strict';
    angular.module('exam.services')
        .factory('examService', ['$translate', '$q', '$location', 'ExamRes', function ($translate, $q, $location, ExamRes) {

            var createExam = function() {
                ExamRes.draft.get(
                    function (response) {
                        toastr.info($translate.instant("sitnet_exam_added"));
                        $location.path("/exams/addcourse/" + response.id);
                    }, function (error) {
                        toastr.error(error.data);
                    });
            };

            var getExamTypeDisplayName = function(type) {
                var name;
                switch (type) {
                    case 'PARTIAL':
                        name = $translate.instant('sitnet_exam_credit_type_partial');
                        break;
                    case 'FINAL':
                        name = $translate.instant('sitnet_exam_credit_type_final');
                        break;
                    default:
                        break;
                }
                return name;
            };

            var getExamGradeDisplayName = function(grade) {
                var name;
                switch (grade) {
                    case 'I':
                        name = 'Improbatur';
                        break;
                    case 'A':
                        name = 'Approbatur';
                        break;
                    case 'B':
                        name = 'Lubenter approbatur';
                        break;
                    case 'N':
                        name = 'Non sine laude approbatur';
                        break;
                    case 'C':
                        name = 'Cum laude approbatur';
                        break;
                    case 'M':
                        name = 'Magna cum laude approbtur';
                        break;
                    case 'E':
                        name = 'Eximia cum laude approbatur';
                        break;
                    case 'L':
                        name = 'Laudatur approbatur';
                        break;
                    case 'REJECTED':
                        name = $translate.instant('sitnet_rejected');
                        break;
                    case 'APPROVED':
                        name = $translate.instant('sitnet_approved');
                        break;
                    default:
                        name = grade;
                        break;
                }
                return name;
            };

            var refreshExamTypes = function () {
                var deferred = $q.defer();
                ExamRes.examTypes.query(function (examTypes) {
                    return deferred.resolve(examTypes.map(function (examType) {
                        examType.name = getExamTypeDisplayName(examType.type);
                        return examType;
                    }));
                });
                return deferred.promise;
            };

            var getScaleDisplayName = function(type) {
                var name;
                var description = type.description || type;
                switch (description) {
                    case 'ZERO_TO_FIVE':
                        name = '0-5';
                        break;
                    case 'LATIN':
                        name = 'Improbatur-Laudatur';
                        break;
                    case 'APPROVED_REJECTED':
                        name = $translate.instant('sitnet_evaluation_select');
                        break;
                    case 'OTHER':
                        name = type.displayName || type;
                }
                return name;
            };

            var refreshGradeScales = function () {
                var deferred = $q.defer();
                ExamRes.gradeScales.query(function (scales) {
                    return deferred.resolve(scales.map(function (scale) {
                        scale.name = getScaleDisplayName(scale);
                        return scale;
                    }));
                });
                return deferred.promise;
            };

            var setExamOwners = function (exam) {
                exam.examTeachers = [];
                exam.teachersStr = "";
                angular.forEach(exam.examOwners, function(owner){
                    if(exam.examTeachers.indexOf(owner.firstName + " " + owner.lastName) === -1) {
                        exam.examTeachers.push(owner.firstName + " " + owner.lastName);
                    }
                });
                exam.teachersStr = exam.examTeachers.map(function(teacher) {
                    return teacher;
                }).join(", ");
            };

            var setExamOwnersAndInspectors = function (exam) {
                exam.examTeachers = [];
                exam.teachersStr = "";

                angular.forEach(exam.examInspections, function (inspection) {
                    if(exam.examTeachers.indexOf(inspection.user.firstName + " " + inspection.user.lastName) === -1) {
                        exam.examTeachers.push(inspection.user.firstName + " " + inspection.user.lastName);
                    }
                });
                var examToCheck = exam.parent || exam;
                angular.forEach(examToCheck.examOwners, function(owner){
                    if(exam.examTeachers.indexOf(owner.firstName + " " + owner.lastName) === -1) {
                        exam.examTeachers.push(owner.firstName + " " + owner.lastName);
                    }
                });
                exam.teachersStr = exam.examTeachers.map(function(teacher) {
                    return teacher;
                }).join(", ");
            };

            var setCredit = function (exam) {
                if(exam.customCredit !== undefined && exam.customCredit) {
                    exam.credit = exam.customCredit;
                } else {
                    exam.course && exam.course.credits ? exam.credit = exam.course.credits : exam.credit = 0;
                }
            };

            var setQuestionColors = function (question) {

                // State machine for resolving how the question header is drawn
                if (question.answered ||
                    (question.answer && question.type === "EssayQuestion" && question.answer.answer && stripHtml(question.answer.answer).length > 0) || // essay not empty
                    (question.answer && question.type === "MultipleChoiceQuestion" && question.answer.option) // has option selected
                ) {
                    question.answered = true;
                    question.questionStatus = $translate.instant("sitnet_question_answered");
                    if (question.expanded) {
                        question.selectedAnsweredState = 'question-active-header';
                    } else {
                        question.selectedAnsweredState = 'question-answered-header';
                    }

                } else {

                    question.questionStatus = $translate.instant("sitnet_question_unanswered");

                    if (question.expanded) {
                        question.selectedAnsweredState = 'question-active-header';
                    } else {
                        question.selectedAnsweredState = 'question-unanswered-header';
                    }
                }
            };

            var stripHtml = function(text) {
                if(text && text.indexOf("math-tex") === -1) {
                    return String(text).replace(/<[^>]+>/gm, '');
                }
                return text;
            };

            // Defining markup outside templates is not advisable, but creating a working custom dialog template for this
            // proved to be a bit too much of a hassle. Lets live with this.
            var getRecordReviewConfirmationDialogContent = function(feedback) {
                return '<h4>' + $translate.instant('sitnet_teachers_comment') + '</h4>'
                    + feedback + '<br/><strong>' + $translate.instant('sitnet_confirm_record_review') + '</strong>';
            };

            return {
                createExam: createExam,
                refreshExamTypes: refreshExamTypes,
                refreshGradeScales: refreshGradeScales,
                getScaleDisplayName: getScaleDisplayName,
                getExamTypeDisplayName: getExamTypeDisplayName,
                getExamGradeDisplayName: getExamGradeDisplayName,
                setExamOwners: setExamOwners,
                setExamOwnersAndInspectors: setExamOwnersAndInspectors,
                setCredit: setCredit,
                setQuestionColors: setQuestionColors,
                stripHtml: stripHtml,
                getRecordReviewConfirmationDialogContent: getRecordReviewConfirmationDialogContent
            };

        }]);
}());
