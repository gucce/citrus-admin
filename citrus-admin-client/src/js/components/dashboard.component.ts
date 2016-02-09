import {Component, OnInit} from 'angular2/core';
import {NgSwitch, NgFor} from 'angular2/common';
import {HTTP_PROVIDERS} from 'angular2/http';
import {Project} from "../model/project";
import {ProjectService} from "../service/project.service";
import {ReportService} from "../service/report.service";
import {TestReport} from "../model/test.report";

@Component({
    templateUrl: 'templates/dashboard.html',
    providers: [ProjectService, ReportService, HTTP_PROVIDERS],
    directives: [NgSwitch, NgFor]
})
export class DashboardComponent implements OnInit {

    constructor(private _projectService: ProjectService,
                private _reportService: ReportService) {}

    errorMessage: string;
    project = new Project();
    testReport = new TestReport();

    ngOnInit() {
        this.getProject();
        this.getTestReport();
    }

    getProject() {
        this._projectService.getActiveProject()
                            .subscribe(
                                project => this.project = project,
                                error => this.errorMessage = <any>error);
    }

    getTestReport() {
        this._reportService.getLatest()
                            .subscribe(
                                report => this.testReport = report,
                                error => this.errorMessage = <any>error);
    }
}