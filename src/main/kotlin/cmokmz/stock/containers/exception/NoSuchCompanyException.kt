package cmokmz.stock.containers.exception

import java.lang.Exception

class NoSuchCompanyException(companyId: Long) : CompanyException("Company \"$companyId\" does not exist")