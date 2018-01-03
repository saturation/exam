/*
 * Copyright (c) 2017 Exam Consortium
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


import toast from 'toastr';

angular.module('app.question')
    .component('libraryOwnerSelection', {
        templateUrl: '/assets/app/question/library/owners/libraryOwners.template.html',
        bindings: {
            selections: '<',
            ownerUpdated: '&'
        },
        controller: ['$translate', 'Question', 'UserRes',
            function ($translate, Question, UserRes) {

                const vm = this;

                vm.$onInit = function () {
                    vm.teachers = UserRes.usersByRole.query({role: 'TEACHER'});
                };

                vm.onTeacherSelect = function ($item, $model, $label) {
                    vm.newTeacher = $item;
                };

                vm.addOwnerForSelected = function () {
                    // check that atleast one has been selected
                    if (vm.selections.length === 0) {
                        toast.warning($translate.instant('sitnet_choose_atleast_one'));
                        return;
                    }
                    if (!vm.newTeacher) {
                        toast.warning($translate.instant('sitnet_add_question_owner'));
                        return;
                    }

                    var data = {
                        'uid': vm.newTeacher.id,
                        'questionIds': vm.selections.join()
                    };

                    Question.questionOwnerApi.update(data,
                        function () {
                            toast.info($translate.instant('sitnet_question_owner_added'));
                            vm.ownerUpdated();
                        }, function () {
                            toast.info($translate.instant('sitnet_update_failed'));
                        });
                };

            }
        ]
    });

