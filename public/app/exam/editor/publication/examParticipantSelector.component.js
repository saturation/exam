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
angular.module('app.exam.editor')
    .component('examParticipantSelector', {
        templateUrl: '/assets/app/exam/editor/publication/examParticipantSelector.template.html',
        bindings: {
            exam: '<'
        },
        controller: ['$translate', 'limitToFilter', 'UserRes', 'Enrolment', 'EnrollRes', 'toast',
            function ($translate, limitToFilter, UserRes, Enrolment, EnrollRes, toast) {

                var vm = this;

                vm.$onInit = function () {
                    vm.newParticipant = {
                        "id": null,
                        "name": null
                    };
                    // go through child exams and read in the enrolments
                    var x = [];
                    vm.exam.children.forEach(function (c) {
                        x = x.concat(c.examEnrolments);
                    });
                    vm.exam.participants = x;
                };

                vm.allStudents = function (filter, criteria) {

                    return UserRes.unenrolledStudents.query({eid: vm.exam.id, q: criteria}).$promise.then(
                        function (names) {
                            return limitToFilter(names, 15);
                        },
                        function (error) {
                            toast.error(error.data);
                        }
                    );
                };

                vm.setExamParticipant = function (item, $model, $label) {
                    vm.newParticipant.id = item.id;
                };

                vm.addParticipant = function () {
                    Enrolment.enrollStudent(vm.exam, vm.newParticipant).then(
                        function (enrolment) {

                            // push to the list
                            vm.exam.examEnrolments.push(enrolment);

                            // nullify input field
                            delete vm.newParticipant.name ;
                            delete vm.newParticipant.id;

                        }, function (error) {
                            toast.error(error.data);

                        });

                };

                vm.removeParticipant = function (id) {
                    EnrollRes.unenrollStudent.remove({id: id}, function () {
                        vm.exam.examEnrolments = vm.exam.examEnrolments.filter(function (ee) {
                            return ee.id !== id;
                        });
                        toast.info($translate.instant('sitnet_participant_removed'));
                    }, function (error) {
                        toast.error(error.data);
                    });
                };

                vm.isActualEnrolment = function (enrolment) {
                    return !enrolment.preEnrolledUserEmail;
                }

            }]
    });
