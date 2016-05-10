package business;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.persistence.EntityManager;
import javax.persistence.Query;

import models.t_bid_loantime_record;
import models.t_bids;
import models.t_bill_invests;
import models.t_bills;
import models.t_invests;
import models.t_products;
import models.t_system_options;
import models.t_user_details;
import models.t_users;
import models.t_wealthcircle_invite;
import models.v_bill_department_haspayed;
import models.v_bill_department_month_maturity;
import models.v_bill_department_overdue;
import models.v_bill_detail;
import models.v_bill_detail_for_collection;
import models.v_bill_detail_for_mark_overdue;
import models.v_bill_has_received;
import models.v_bill_haspayed;
import models.v_bill_invests_overdue_unpaid;
import models.v_bill_invests_paid;
import models.v_bill_invests_payables_statistics;
import models.v_bill_invests_pending_payment;
import models.v_bill_invests_principal_advances;
import models.v_bill_loan;
import models.v_bill_month_maturity;
import models.v_bill_overdue;
import models.v_bill_receiving;
import models.v_bill_receiving_overdue;
import models.v_bill_recently_pending;
import models.v_bill_receviable_statistical;
import models.v_bill_repayment_record;
import models.v_user_detail_credit_score_normal_repayment;
import models.v_user_detail_credit_score_overdue;
import net.sf.json.JSONObject;

import org.apache.commons.lang.StringUtils;

import play.Logger;
import play.db.helper.JpaHelper;
import play.db.jpa.JPA;
import utils.Arith;
import utils.DataUtil;
import utils.DateUtil;
import utils.ErrorInfo;
import utils.JPAUtil;
import utils.NumberUtil;
import utils.PageBean;
import utils.PushMessage;
import utils.QueryUtil;
import business.Optimization.BillOZ;

import com.shove.Convert;

import constants.Constants;
import constants.DealType;
import constants.SQLTempletes;
import constants.SupervisorEvent;
import constants.Templets;
import constants.UserEvent;

/**
 * 账单
* @author zhs
* @version 6.0
* @created 2014年3月21日 下午2:19:20
 */

public class Bill implements Serializable{
    public long id;
    private long _id;
    
 	public long bidId;
 	public String title;
 	public Date repaymentTime;
 	public double repaymentCorpus;
 	public double repaymentInterest;
 	public int status;
 	public String merBillNo;
 	public int periods;
 	public Date realRepaymentTime;
 	public double realRepaymentCorpus;
 	public double realRepaymentInterest;
 	public int overdueMark;
 	public Date markOverdueTime;
 	public double overdueFine;
 	public Date markBadTime;
 	public int noticeCountMessage;
 	public int noticeCountMail;
 	public int noticeCountTelphone;
 	
 	public User user;
    public Bid bid;
    
    public boolean isRepair;//是否补单
    
    // v7.2.6 add 
    public int ipsStatus;  //资金托管处理状态
    
    public long getBidId() {
		return bidId;
	}

	public void setBidId(long bidId) {
		this.bidId = bidId;
	}

	public void setId(long id){
    	ErrorInfo error = new ErrorInfo();
    	t_bills bills = null;
    	 try {
    		 bills = t_bills.findById(id);
		} catch (Exception e) {
			e.printStackTrace();
			Logger.info("查询账单详情时："+e.getMessage());
			error.code = -1;
			error.msg = "由于数据库异常，导致账单详情失败！";
		}

		if(bills.id < 0 || bills == null){
    		 this._id = -1;
    		 return;
    	 }
    	 
    	 this._id = bills.id;
    	 this.bidId = bills.bid_id;
    	 this.title = bills.title;
         this.repaymentTime = bills.repayment_time;
         this.repaymentCorpus = bills.repayment_corpus;
         this.repaymentInterest = bills.repayment_interest;
         this.status = bills.status;
         this.merBillNo = bills.mer_bill_no;
         this.periods = bills.periods;
         this.realRepaymentTime = bills.real_repayment_time;
         this.realRepaymentCorpus = bills.real_repayment_corpus;
         this.realRepaymentInterest = bills.real_repayment_interest;
         this.overdueMark = bills.overdue_mark;
         this.markOverdueTime = bills.mark_overdue_time;
         this.overdueFine = bills.overdue_fine;
         this.markBadTime = bills.mark_bad_time;
         this.noticeCountMessage = bills.notice_count_message;
         this.noticeCountMail = bills.notice_count_mail;
         this.noticeCountTelphone = bills.notice_count_telphone;
         
         this.ipsStatus = bills.ips_status; // v7.2.6 add 
         
    	 bid = new Bid();
    	 bid.id = bills.bid_id;
    	 
         this.user = bid.user;
     }
     
     public long getId() {
 		return _id;
 	}
     
     
     public int getStatus() {
		return status;
	}

	public int setStatus(int status,long id) {
		String sql = "update t_bill_invests set status = ? where bid_id= ?";
		Query query1 = JPA.em().createQuery(sql);
		 query1.setParameter(1, status); 
		 query1.setParameter(2, id);
		int  row = query1.executeUpdate();
		return row;
	}

	/**
      * 日期累加
      * @param date
      * @param type
      * @param value
      * @return
      */
     public static Date add(Date date, int type, int value){
		 Calendar calendar = Calendar.getInstance();
		 calendar.setTime(date);
		 calendar.add(type, value);
		 
		 return calendar.getTime();
	 }
	  
     /**
      * 查询投资管理费率和逾期费率
      * @param info
      * @return
      */
     public static Map<String, Object> checkManagerate(ErrorInfo error){
    	 error.clear();
    	 List<t_system_options> options = null;
    	 
    	 double  investmentRates;
    	 double  overdueRates;
    	 
    	 String sql = "_key = ? or _key = ? order by id";
    	 try {
    		 options = t_system_options.find(sql, "investment_fee","overdue_fee").fetch();
		} catch (Exception e) {
			e.printStackTrace();
			Logger.info("查询投资管理费率时："+e.getMessage());
			error.code = -1;
			error.msg = "由于数据库异常，导致查询查询投资管理费率失败！";
			
			return null;
		}
		 investmentRates = Double.parseDouble(options.get(0)._value); 
		 overdueRates = Double.parseDouble(options.get(1)._value);
    	 
    	 Map<String,Object> map = new HashMap<String,Object>();
    	 map.put("investmentRates", investmentRates);
    	 map.put("overdueRates", overdueRates);
    	 
    	 return map;
     }
     
	/**
 	 * 保存账单
 	 * @param obj
 	 */
 	public int addBill(Bid bid,double nowAmount, ErrorInfo error){
 		error.clear();
 		
 		double dayRate = 0;//日利率
 		double totalInterest = 0;//总利息
 		
 		int deadline = bid.period; //借款标期限
 		double borrowAmount = bid.amount; //借款金额
 		//monthRate = Double.valueOf(bid.apr * 0.01)/12.0;//通过年利率得到月利率
 		dayRate = Double.valueOf(bid.apr * 0.01)/365.0;//通过年利率得日利率
 		//double monPayInterest = Arith.mul(Arith.mul(Arith.div(bid.apr/100, 365,6), bi.receive_corpus), days);//申请赎的回标的总利息

       if(bid.repayment.id == Constants.ONCE_REPAYMENT){
    	  
		   this.addDayBills(bid.title, deadline, borrowAmount, dayRate, 
				   bid.id, bid.repayment.id, bid.userId, bid.isSecBid,totalInterest,nowAmount, error);//生成天标借款账单和投资账单
		   
		   return error.code;
 		
	    }
       
       return error.code;
 	}
 	
 	/**
 	 * 生成天标借款账单
 	 * @param deadline 借款天标的天数
 	 * @param borrowSum  借款的总额
 	 * @param monthRate  月利率
 	 * @param bidId   标id
 	 */
 	public int addDayBills(String title, int deadline, double borrowAmount, double dayRate,
 			long bidId, long repaymentId, long userId, boolean isSecBid, double totalInterest,double nowAmount, ErrorInfo error){
 		error.clear();
 		
	    t_bills bills = new t_bills();
	 	double monPayInterest = Arith.div(Arith.mul(Arith.mul(borrowAmount, dayRate), deadline), 1, 2);//天标的总利息  从借款第一天开始
	    Bid  bid1= new Bid();
	    bid1.id = bidId; 
		//1.生成借款账单
		bills.bid_id = bidId;
		bills.title = title;
		bills.periods = 1;
		if(bid1.isRegular){
			bills.repayment_time = add(bid1.time, Calendar.DAY_OF_MONTH, bid1.period);

		}else{
			bills.repayment_time = add(new Date(), Calendar.DAY_OF_MONTH, deadline);

		}
		bills.repayment_corpus = borrowAmount;
		bills.repayment_interest = monPayInterest;
		bills.status = Constants.NO_REPAYMENT;
		bills.mer_bill_no = User.createBillNo();
		try {
			bills.save();
			
	  } catch(Exception e) {
			e.printStackTrace();
			Logger.info("生成天标借款账单时："+e.getMessage());
			error.code = -1;
			error.msg = "数据库异常，导致生成天标借款账单失败";
			JPA.setRollbackOnly();
		}
	  
	   this.addInvestBills(bidId, repaymentId, userId, isSecBid, error);//生成投资账单
	   
	   double  totalrate = 0;   //总利息
	   //如果是活标
        if(bid1.isRegular){ 
        	
        	double  personrate = 0;  //每个人的利息
        	int     days = 0;        //相差的天数
        	int     row  = 0;
        	
        	//查出所有投资人记录
        	List<Long> ids = new ArrayList<Long>();
        	String sql = "select id as invest_id from t_invests where bid_id = ?" ;
        	
        	Query query =JPA.em().createQuery(sql).setParameter(1, bidId); 
        	ids = query.getResultList();
        	
        	Invest invest = new Invest();
        	//算出该借款人  活标应付总利息
        	for (Long id : ids) {
        		invest.id = id;
        		 days = DateUtil.daysBetween(bid1.time,invest.time);
        		 days = bid1.period - days;
        		 if(days != 0){
        			//天标的每个人利息
            		 personrate = Arith.div(Arith.mul(Arith.mul(invest.amount, dayRate), days), 1, 2);  
                    //更新每个投资人的理财账单
            		 sql="update t_bill_invests set  receive_interest = ?  where  invest_id = ?  and  periods = ? ";
            		 Query query1 = JPA.em().createQuery(sql);
     				 query1.setParameter(1, personrate); //投资人利息
     				 query1.setParameter(2, id);// 理财账单id
     				 query1.setParameter(3, Constants.ONE);// 期数（只有一期）
     				 try {
     					
     					 row = query1.executeUpdate();
     				 } catch (Exception e) {
     					e.printStackTrace();
     					Logger.error("定期标->生成投资账单->:" + e.getMessage());
     					error.code = -1;
     					error.msg = "修改定期标生成投资账单失败!";
     					JPA.setRollbackOnly();

     					return error.code;
     				}
     				if(row<1){
     					JPA.setRollbackOnly();
     					error.code = -1;
     					error.msg="修改投资表定期标生成投资账单出错！";
     					return error.code;
     				}
        		 }
        		 totalrate += personrate;
        		 
        	    t_bid_loantime_record  loantime = new t_bid_loantime_record();	 
        	    loantime.bid_id = bidId;
        	    loantime.invest_id = id;
        	    loantime.loan_time = new Date();
        	    loantime.invest_time = invest.time;
        	    loantime.deadline_time = add(new Date(), Calendar.DAY_OF_MONTH, bid1.period);
        	    try {
					
					  loantime.save();
				 } catch (Exception e) {
					e.printStackTrace();
					Logger.error("定期标->增加理财账单返息时间表->:" + e.getMessage());
					error.code = -1;
					error.msg = "定期标->增加理财账单返息时间表失败!";
					JPA.setRollbackOnly();

					return error.code;
				}
        	}
        	
        	//修改借款人账单 应还总利息
        	 sql="update t_bills set repayment_interest = ? where bid_id = ? and periods = ?";
        	     query = JPA.em().createQuery(sql);
				 query.setParameter(1, totalrate); //借款总利息
				 query.setParameter(2, bidId);// 标的id
				 query.setParameter(3, Constants.ONE);// 期数（只有一期）
				 try {
					
					 row = query.executeUpdate();
				 } catch (Exception e) {
					e.printStackTrace();
					Logger.error("定期标->修改借款账单->:" + e.getMessage());
					error.code = -1;
					error.msg = "修改定期标修改借款账单失败!";
					JPA.setRollbackOnly();

					return error.code;
				}
				if(row<1){
					JPA.setRollbackOnly();
					error.code = -1;
					error.msg="修改定期借款账单出错！";
					return error.code;
				}
        }else{
        	totalrate = monPayInterest;
        }
        
        //活标，散标生成账单后都要先扣除借款人借款总利息
        DealDetail.minusUserFund(userId, totalrate, error);
        
		Map<String, Double> detail = null;
		//及时查询用户最新状态
		detail = DealDetail.queryUserFund(userId, error);
		
		DealDetail dealDetail = new DealDetail(userId, DealType.M_ALLRATE,
				totalrate, bidId, detail.get("user_amount"), detail.get("freeze"),
				0.00, "扣除借款总利息");
	    //添加交易记录
		dealDetail.addDealDetail(error);
		
		//由于短信模板 需要扣除总利息，因此在生成账单中发送短信
		TemplateSms sms = new TemplateSms();
		if(bid1.isRegular){
		  sms.setId(Templets.S_REGULAR_FINANC_RELEASE);
		}else{
		  sms.setId(Templets.S_FINANC_RELEASE);
		}
		String content = null;
	
			//尊敬的userName: 您申请的编号bidId借款标已成功放款，借款金额amount元，扣除管理费serviceFees元
			if(sms.status && StringUtils.isNotBlank(bid1.user.mobile)){
				BackstageSet backstageSet = BackstageSet.getCurrentBackstageSet();
				content = sms.content;
				content = content.replace("userName", bid1.user.name);
				content = content.replace("bidId",backstageSet.loanNumber+bid1.id);
				content = content.replace("amount",DataUtil.formatString(bid1.amount)); 
				content = content.replace("serviceFees",DataUtil.formatString(bid1.serviceFees));
				content = content.replace("totalrate",DataUtil.formatString(totalrate));
				if(bid1.isRegular){
				   content = content.replace("money1",DataUtil.formatString(nowAmount));
				}
				content = content.replace("money",DataUtil.formatString(bid1.amount - bid1.serviceFees -totalrate ));//到账金额
				sms.addSmsTask(bid1.user.mobile, content);
			
		}
		
	   error.code = 0;
	   return error.code;
 	}
 	
 	/**
 	 * 生成投资账单
 	 * @param deadline 借款期限
 	 * @param borrowSum  借款总额
 	 * @param monPayAmount  月收金额
 	 * @param monPayRate  月利息
 	 * @param bidId  标id
 	 */
 	public int addInvestBills(long bidId, long repaymentId, long userId , boolean isSecBid, ErrorInfo error){
 		  error.clear();
 		  EntityManager em = JPA.em();
 		
 		  //1.生成投资账单
 		  String sql = "insert into t_bill_invests(user_id,invest_id,bid_id,mer_bill_no,periods,title,receive_time,receive_corpus,receive_interest, " +
	  		"status, overdue_fine, real_receive_corpus, real_receive_interest) SELECT a.user_id,a.id, a.bid_id,a.mer_bill_no,b.periods,b.title,b.repayment_time,truncate(((a.amount * b.repayment_corpus)/ c.amount),2)," +
	  		"truncate(((a.amount * b.repayment_interest)/ c.amount),2), -1, 0.00, 0.00, 0.00 FROM t_bills AS b LEFT JOIN t_invests AS a ON a.bid_id " +
	  		"= b.bid_id LEFT JOIN t_bids AS c ON a.bid_id = c.id AND b.bid_id = c.id WHERE b.bid_id IS NOT NULL AND b.status " +
	  		"= -1 AND b.bid_id = ?";
 		  
		  Query query = em.createNativeQuery(sql).setParameter(1, bidId);
		  int rows = 0;
		    
		  try {
		  	    rows = query.executeUpdate();
		  } catch(Exception e) {
				e.printStackTrace();
				Logger.info("添加投资账单时："+e.getMessage());
				error.code = -3;
				error.msg = "数据库异常，导致添加投资账单失败";
				JPA.setRollbackOnly();
					
				return error.code;
			}
			  
			 if(rows == 0){
				error.code = -1;
				error.msg = "添加投资账单操作数据库没有改变";
				JPA.setRollbackOnly();
					
				return error.code;
			}
			 
			 //2.初始化纠偏数据(投资记录表):查询出的理财账单的收取本金总和和利息总和赋值给t_invests标的纠偏字段
		  String correctStartSql = "update t_invests t1, (select a.invest_id, a.bid_id, a.user_id, sum(a.receive_corpus) receive_corpus, sum(a.receive_interest)" +
		  		" receive_interest from t_bill_invests a where a.bid_id = ? group by a.invest_id) t2 set t1.correct_amount = t2.receive_corpus ," +
		  		" t1.correct_interest = t2.receive_interest where t1.bid_id = t2.bid_id and t1.id = t2.invest_id";
		  
		  Query correctStart = em.createNativeQuery(correctStartSql).setParameter(1, bidId);
		    
		  try {
		  	    rows = correctStart.executeUpdate();
		  } catch(Exception e) {
				e.printStackTrace();
				Logger.info("纠偏数据初始化时："+e.getMessage());
				error.code = -3;
				error.msg = "数据库异常，导致纠纠偏数据初始化失败";
				JPA.setRollbackOnly();
					
				return error.code;
			}
		  
		  //3.纠偏本金利息给第一个投资人(投资记录表)：t_bills表要还款的本金总金额和利息总金额分别减去t_invests纠偏本金总和和纠偏利息总金额得到的差值再分别加到第一个投资人纠偏本金和纠偏利息上
		  String correctCorIntSql = "update t_invests t1, (select t3.min_id, (t4.repayment_corpus - t3.collect_amount) check_amount, " +
		  		"(t4.repayment_interest - t3.collect_interest) check_interest from (select min(a.id) min_id, a.bid_id, sum(a.correct_amount) " +
		  		"collect_amount, sum(a.correct_interest) collect_interest from t_invests a where a.bid_id = ? group by a.bid_id) t3 left join " +
		  		"(select b.bid_id, sum(b.repayment_corpus) repayment_corpus, sum(b.repayment_interest) repayment_interest from  t_bills b where " +
		  		"b.bid_id = ? group by b.bid_id) t4 on t3.bid_id = t4.bid_id) t2 set t1.correct_amount = t1.correct_amount + t2.check_amount, " +
		  		"t1.correct_interest = t1.correct_interest + t2.check_interest where t1.id = t2.min_id";
		  
		  Query correctCorInt = em.createNativeQuery(correctCorIntSql).setParameter(1, bidId).setParameter(2, bidId);
		    
		  try {
		  	    rows = correctCorInt.executeUpdate();
		  } catch(Exception e) {
				e.printStackTrace();
				Logger.info("纠偏数据初始化时："+e.getMessage());
				error.code = -3;
				error.msg = "数据库异常，导致纠偏本金利息失败";
				JPA.setRollbackOnly();
					
				return error.code;
			}
		  
		  //4.核对纠偏本金(投资记录表)
		  String checkCorrectSql = "update t_invests t1,(select a.id, a.user_id, a.bid_id, (a.amount - a.correct_amount) check_corpus from t_invests a " +
		  		"where a.bid_id = ?) t2 set t1.correct_amount = t1.correct_amount + t2.check_corpus where t1.bid_id = t2.bid_id and t1.user_id = t2.user_id and t2.id = t1.id";
		  //update t_invests t1,(select a.user_id, a.bid_id, (a.amount - a.correct_amount) check_corpus from t_invests a where a.bid_id = 41 group by a.user_id) t2 set t1.correct_amount = t1.correct_amount + t2.check_corpus, t1.correct_interest = t1.correct_interest + t2.check_corpus where t1.bid_id = t2.bid_id and t1.user_id = t2.user_id
			 
		  Query checkCorrect = em.createNativeQuery(checkCorrectSql).setParameter(1, bidId);
		    
		  try {
		  	    rows = checkCorrect.executeUpdate();
		  } catch(Exception e) {
				e.printStackTrace();
				Logger.info("纠偏数据初始化时："+e.getMessage());
				error.code = -3;
				error.msg = "数据库异常，导致纠偏本金失败";
				JPA.setRollbackOnly();
					
				return error.code;
			}
			
		//5.纠偏利息给第一个投资人(投资记录表)：t_bills里的 和t_invests表的所有本金和本金，利息和利息相减得到的差值给第一个投资人
		  String correctIntSql = "update t_invests t1, (select t3.min_id, (t4.repayment_corpus - t3.collect_amount) check_amount, " +
		  		"(t4.repayment_interest - t3.collect_interest) check_interest from (select min(a.id) min_id, a.bid_id, sum(a.correct_amount) " +
		  		"collect_amount, sum(a.correct_interest) collect_interest from t_invests a where a.bid_id = ? group by a.bid_id) t3 left join " +
		  		"(select b.bid_id, sum(b.repayment_corpus) repayment_corpus, sum(b.repayment_interest) repayment_interest from  t_bills b where " +
		  		"b.bid_id = ? group by b.bid_id) t4 on t3.bid_id = t4.bid_id) t2 set t1.correct_amount = t1.correct_amount + t2.check_amount, " +
		  		"t1.correct_interest = t1.correct_interest + t2.check_interest where t1.id = t2.min_id";
		  
		  Query correctInt = em.createNativeQuery(correctIntSql).setParameter(1, bidId).setParameter(2, bidId);
		    
		  try {
		  	    rows = correctInt.executeUpdate();
		  } catch(Exception e) {
				e.printStackTrace();
				Logger.info("纠偏数据初始化时："+e.getMessage());
				error.code = -3;
				error.msg = "数据库异常，导致利息失败";
				JPA.setRollbackOnly();
					
				return error.code;
			}
			 
			//6.纠偏投资应收款明细资金(理财账单表)：t_bills和t_bill_invests根据算出每一期每个人的本金利息差值都给到第一个投资人的本金利息上
 		  String updateSql = "update t_bill_invests t1,(select c.minId, (a.repayment_corpus-b.recivedPrincipal) check_corpus," +
	  		"(a.repayment_interest-b.recivedInterest) check_interest from (select id, bid_id,periods," +
	  		"repayment_corpus,repayment_interest from t_bills where bid_id = ?) a left join (select a.id ,a.bid_id, a.periods, " +
	  		"sum(a.receive_corpus) recivedPrincipal, sum(a.receive_interest) recivedInterest from t_bill_invests a where a.bid_id	= ? " +
	  		"group by a.periods) b on a.bid_id = b.bid_id AND a.periods = b.periods  left join (select min(a.id) minId,a.bid_id," +
	  		"a.periods from t_bill_invests a where a.bid_id = ? group by a.periods) c on b.bid_id = c.bid_id AND a.periods = c.periods) " +
	  		"t2 set t1.receive_corpus = t1.receive_corpus + t2.check_corpus, t1.receive_interest = t1.receive_interest + " +
	  		"t2.check_interest where t1.id = t2.minId";
 		  
		  Query update = em.createNativeQuery(updateSql).setParameter(1, bidId).setParameter(2, bidId).setParameter(3, bidId);
		    
		  try {
		  	    rows = update.executeUpdate();
		  } catch(Exception e) {
				e.printStackTrace();
				Logger.info("纠偏投资应收款明细资金时："+e.getMessage());
				error.code = -3;
				error.msg = "数据库异常，导致纠偏投资应收款明细资金失败";
				JPA.setRollbackOnly();
					
				return error.code;
			}
		  //7.纠偏待收本金和利息(理财账单表)：t_invest 和t_bill_invests把本金和本金，利息和利息的差值都给到第一个投资人那里 ,+
		  
		  
		  String updateCorIntSql = "update t_bill_invests t1,(select t3.id, t3.min_id, t3.user_id, t3.bid_id, (t4.amount - t3.receive_corpus) check_corpus, " +
	  		"(t4.correct_interest - t3.receive_interest) check_interest from (select a.id, min(a.id) as min_id, a.invest_id, a.bid_id, a.user_id, sum(a.receive_corpus) " +
	  		"receive_corpus, sum(a.receive_interest) receive_interest from t_bill_invests a where a.bid_id = ? group by a.invest_id) " +
	  		"t3 left join (select b.user_id, b.id, b.bid_id, b.amount, b.correct_interest from t_invests b where b.bid_id = ? group by" +
	  		" b.id) t4 on t3.bid_id = t4.bid_id and t3.invest_id = t4.id) t2 set t1.receive_corpus = t1.receive_corpus + " +
	  		"t2.check_corpus, t1.receive_interest = t1.receive_interest + t2.check_interest where t1.id = t2.min_id";
	  
	      Query updateCorInt = em.createNativeQuery(updateCorIntSql).setParameter(1, bidId).setParameter(2, bidId);
	    
		  try {
		  	    rows = updateCorInt.executeUpdate();
		  } catch(Exception e) {
				e.printStackTrace();
				Logger.info("纠偏投资应收款明细资金时："+e.getMessage());
				error.code = -3;
				error.msg = "数据库异常，导致纠偏待收本金和利息失败";
				JPA.setRollbackOnly();
					
				return error.code;
			}
		  
		  if(repaymentId == Constants.PAID_MONTH_ONCE_REPAYMENT){
			   //8.先息后本账单
			  String oprateSql = "update t_bill_invests t1,(select a.receive_corpus, a.id from t_bill_invests a left join t_bids b on a.bid_id = " +
			  		"b.id where a.periods < b.period and a.bid_id = ? and b.period_unit <> ? group by a.id) t2 set t1.receive_corpus = 0.00, t1.receive_interest = " +
			  		"t1.receive_interest + t2.receive_corpus where t1.id = t2.id";
		  
		      Query oprateInvestBill = em.createNativeQuery(oprateSql).setParameter(1, bidId).setParameter(2, Constants.DAY);
		    
			  try {
			  	    rows = oprateInvestBill.executeUpdate();
			  } catch(Exception e) {
					e.printStackTrace();
					Logger.info("纠偏投资应收款明细资金时："+e.getMessage());
					error.code = -3;
					error.msg = "数据库异常，导致纠偏待收本金和利息失败";
					JPA.setRollbackOnly();
						
					return error.code;
				}
		  }  
		
		  //普通网关，秒还标还款逻辑
		  if(isSecBid && !Constants.IPS_ENABLE){			  
			  //秒还标普通网关还款逻辑，及资金托管请求参数查询逻辑
			  this.repayment(userId, error);
			/*if(Constants.IPS_ENABLE && error.code >= 0) {
					
					Bill bill = new Bill();
					bill.id = this._id;
					
					PaymentProxy.getInstance().autoRepayment(error, Constants.PC, bill);
					if(error.code < 0) {
						error.code = -1;
						error.msg = "秒还标还款失败";
						JPA.setRollbackOnly();
							
						return error.code;
					}
			}*/
			  
		  }
		  
			error.code = 0;
			return error.code;
 	  }
 	
 	/**
 	 * 根据userId查询用户可用余额
 	 * @param error
 	 * @return
 	 */
 	public static double queryBalance(long userId, ErrorInfo error){
 		String sql2 = "select balance from t_users where id = ? ";
 		Double balance;
		try {
			balance = t_users.find(sql2, userId).first();
			
		} catch(Exception e) {
			e.printStackTrace();
			Logger.info("查询投资人金额时："+e.getMessage());
			error.code = -2;
			error.msg = "数据库异常，导致查询投资人金额失败";
			JPA.setRollbackOnly();
			
			return error.code;
		}

		return balance = null == balance ? 0 : balance.doubleValue();
 		
 	}
 	
 	/**
 	 * 管理员放款理财账单
 	 * @param investId
 	 * @param error
 	 * @return
 	 */
 	public static int investForPayment(long investId, ErrorInfo error){
 		error.clear();
 		
 		EntityManager em = JPA.em();
 		int rows = 0;
 		Map<String, Object> investMap = new HashMap<String, Object>();
 		Bill bill = new Bill();

 		//1.查询理财账单的相关信息
		String sql = "select new Map(status as status, invest_id as investId, user_id as user_id, receive_corpus as receive_corpus," +
				" receive_interest as receive_interest, overdue_fine as overdue_fine, periods as period, bid_id as bid_id)" +
				" from t_bill_invests where id = ? and status in (?,?)";
		try {
			investMap = t_bill_invests.find(sql, investId, Constants.FOR_PAY, Constants.FOR_OVERDUE_PAY).first();
		} catch(Exception e) {
			e.printStackTrace();
			Logger.info("查询理财账单信息时："+e.getMessage());
			error.code = -1;
			error.msg = "数据出现异常，导致付款失败";
			JPA.setRollbackOnly();
			
			return error.code;
		}
		
		if(null == investMap){
			error.code = -1;
			error.msg = "数据出现异常，导致付款失败";
			JPA.setRollbackOnly();
			
			return error.code;
		}
 		
		long investUserId = (Long) investMap.get("user_id");//投资人id
		long bidId = (Long) investMap.get("bid_id");//标id
		long investsId = (Long) investMap.get("investId");//投资记录Id
		int period = (Integer) investMap.get("period");//期数
		int status = (Integer) investMap.get("status");//收款状态
		double receiveCorpus = (Double) investMap.get("receive_corpus");//投资本金
		double receiveInterest = (Double) investMap.get("receive_interest");//投资利息
		double overDueFine = (Double) investMap.get("overdue_fine");//逾期罚息
		double shouldReceiveAmount = Arith.round(receiveCorpus + receiveInterest + overDueFine, 2);//应收金额
		
		double investmentRate = Bid.queryInvestRate(bidId); 

		if(investmentRate != 0){
			investmentRate = investmentRate / 100;
		}
		
		double manageFee = BillInvests.getInvestManagerFee(receiveInterest, investmentRate, investUserId);
		double amount = Arith.round((receiveCorpus + receiveInterest + overDueFine - manageFee), 2);//用户总共获得的金额 
		
		if(status != Constants.FOR_PAY && status != Constants.FOR_OVERDUE_PAY){
			error.code = -1;
			error.msg = "该理财账单已付款";
			
			return error.code;
		}
		
		//4.改变投资账单的收款状态
		//逾期付款
		if(status == Constants.FOR_OVERDUE_PAY){
			String overdueSql = "update t_bill_invests set status = ?, real_receive_time = ?, real_receive_corpus = ?, " +
					"real_receive_interest = ?, overdue_fine = ? where id =? and status = ? and invest_id = ?";
			
			Query overdue = em.createQuery(overdueSql).setParameter(1, Constants.OVERDUE_RECEIVABLES).
			setParameter(2, DateUtil.currentDate()).setParameter(3, receiveCorpus).setParameter(4, receiveInterest)
			.setParameter(5, overDueFine).setParameter(6, investId).setParameter(7, Constants.FOR_OVERDUE_PAY).setParameter(8, investsId);
			
			try {
				rows = overdue.executeUpdate();
			} catch(Exception e) {
				e.printStackTrace();
				Logger.info("修改理财账单收款情况时："+e.getMessage());
				error.code = -2;
				error.msg = "数据出现异常，导致付款失败";
				JPA.setRollbackOnly();
				
				return error.code;
			}
			
			if(rows == 0){
				error.code = -1;
				error.msg = "数据出现异常，导致付款失败";
				JPA.setRollbackOnly();
				
				return error.code;
			}
			
			//投资人添加本金和利息
			String userBalanceSql = "update t_users set balance = balance + ? where id =?";
			
			Query userBalance = em.createQuery(userBalanceSql).setParameter(1, receiveCorpus + receiveInterest).setParameter(2, investUserId);
			
			try {
				rows = userBalance.executeUpdate();
			} catch(Exception e) {
				e.printStackTrace();
				Logger.info("修改用户可用金额时："+e.getMessage());
				error.code = -2;
				error.msg = "数据出现异常，导致修改用户可用金额失败";
				JPA.setRollbackOnly();
				
				return error.code;
			}
			
			if(rows == 0){
				error.code = -1;
				error.msg = "数据出现异常，导致付款失败";
				JPA.setRollbackOnly();
				
				return error.code;
			}
			
			Map<String, Double> investForDetail = DealDetail.queryUserFund(investUserId, error);
			if(error.code < 0 || investForDetail == null) {
				JPA.setRollbackOnly();
				
				return -1;
			}
			
			double investFreeze = investForDetail.get("freeze");
			double investReceiveAmount = investForDetail.get("receive_amount");
			
			Double investBalance10 = bill.queryBalance(investUserId, error);
			
			DealDetail investDetail = new DealDetail(investUserId, DealType.OVER_RECEIVE, receiveCorpus + receiveInterest,
					investId, investBalance10, investFreeze, investReceiveAmount , "逾期收款获取第"+investId+"号账单投资金额");
			
			//添加逾期收款的交易记录
			investDetail.addDealDetail(error);
			if(error.code < 0) {
				JPA.setRollbackOnly();
				
				return -1;
			}
			
			//投资人添加罚息
			String userBalanceSql2 = "update t_users set balance = balance + ? where id =?";
			
			Query userBalance2 = em.createQuery(userBalanceSql2).setParameter(1, overDueFine).setParameter(2, investUserId);
			
			try {
				rows = userBalance2.executeUpdate();
			} catch(Exception e) {
				e.printStackTrace();
				Logger.info("修改用户可用金额时："+e.getMessage());
				error.code = -2;
				error.msg = "数据出现异常，导致修改用户可用金额失败";
				JPA.setRollbackOnly();
				
				return error.code;
			}
			
			if(rows == 0){
				error.code = -1;
				error.msg = "数据出现异常，导致付款失败";
				JPA.setRollbackOnly();
				
				return error.code;
			}
			
			Double investBalance3 = bill.queryBalance(investUserId, error);
			
			DealDetail investOverdueFeeDetail = new DealDetail(investUserId, DealType.ADD_OVERDUE_FEE, overDueFine,
					investId, investBalance3, investFreeze, investReceiveAmount, "获取第"+investId+"号账单逾期费");
			
			//添加获取逾期费用的交易记录
			investOverdueFeeDetail.addDealDetail(error);
			if(error.code < 0) {
				JPA.setRollbackOnly();
				
				return -1;
			}
			//正常收款
		}else{
            String normalSql = "update t_bill_invests set status = ?, real_receive_time = ?, real_receive_corpus = ?, " +
            		"real_receive_interest = ?, overdue_fine = ? where id =? and status = ? and invest_id = ?";
			
			Query normal = em.createQuery(normalSql).setParameter(1, Constants.NORMAL_RECEIVABLES).
			setParameter(2, DateUtil.currentDate()).setParameter(3, receiveCorpus).setParameter(4, receiveInterest)
			.setParameter(5, 0.00).setParameter(6, investId).setParameter(7, Constants.FOR_PAY).setParameter(8, investsId);
			
			try {
				rows = normal.executeUpdate();
			} catch(Exception e) {
				e.printStackTrace();
				Logger.info("修改理财账单收款情况时："+e.getMessage());
				error.code = -2;
				error.msg = "数据出现异常，导致付款失败";
				JPA.setRollbackOnly();
				
				return error.code;
			}
			
			if(rows == 0){
				error.code = -1;
				error.msg = "数据出现异常，导致付款失败";
				JPA.setRollbackOnly();
				
				return error.code;
			}
			
			//投资人本金和利息
			String userBalanceSql2 = "update t_users set balance = balance + ? where id =?";
			
			Query userBalance2 = em.createQuery(userBalanceSql2).setParameter(1, receiveCorpus + receiveInterest).setParameter(2, investUserId);
			
			try {
				rows = userBalance2.executeUpdate();
			} catch(Exception e) {
				e.printStackTrace();
				Logger.info("修改用户可用金额时："+e.getMessage());
				error.code = -2;
				error.msg = "数据出现异常，导致修改用户可用金额失败";
				JPA.setRollbackOnly();
				
				return error.code;
			}
			
			if(rows == 0){
				error.code = -1;
				error.msg = "数据出现异常，导致付款失败";
				JPA.setRollbackOnly();
				
				return error.code;
			}
			
			Map<String, Double> investForDetail3 = DealDetail.queryUserFund(investUserId, error);
			if(error.code < 0 || investForDetail3 == null) {
				JPA.setRollbackOnly();
				
				return -1;
			}
			
			double investFreeze3 = investForDetail3.get("freeze");
			double investReceiveAmount3 = investForDetail3.get("receive_amount");
			
			Double investBalance9 = bill.queryBalance(investUserId, error);
			
			DealDetail investDetail = new DealDetail(investUserId, DealType.NOMAL_RECEIVE, receiveCorpus + receiveInterest,
					investId, investBalance9, investFreeze3, investReceiveAmount3, "正常收款获取第"+investId+"号账单投资金额");
			
			//添加收款的交易记录
			investDetail.addDealDetail(error);
			if(error.code < 0) {
				JPA.setRollbackOnly();
				
				return -1;
			}
		}
		
		//减去投资人管理费
		String userBalanceSql2 = "update t_users set balance = balance - ? where id =? and balance >= ?";
		
		Query userBalance2 = em.createQuery(userBalanceSql2).setParameter(1, manageFee).setParameter(2, investUserId).setParameter(3, manageFee);
		
		try {
			rows = userBalance2.executeUpdate();
		} catch(Exception e) {
			e.printStackTrace();
			Logger.info("修改用户可用金额时："+e.getMessage());
			error.code = -2;
			error.msg = "数据出现异常，导致修改用户可用金额失败";
			JPA.setRollbackOnly();
			
			return error.code;
		}
		
		if(rows == 0){
			error.code = -1;
			error.msg = "数据出现异常，导致付款失败";
			JPA.setRollbackOnly();
			
			return error.code;
		}
		
		Map<String, Double> investForDetail4 = DealDetail.queryUserFund(investUserId, error);
		if(error.code < 0 || investForDetail4 == null) {
			JPA.setRollbackOnly();
			
			return -1;
		}
		
		double investFreeze4 = investForDetail4.get("freeze");
		double investReceiveAmount4 = investForDetail4.get("receive_amount");
		
		Double investBalance2 = bill.queryBalance(investUserId, error);
		
		DealDetail investFeeDetail = new DealDetail(investUserId, DealType.CHARGE_INVEST_FEE, manageFee,
				investId, investBalance2, investFreeze4, investReceiveAmount4, "扣除第"+investId+"号账单理财管理费");
		
		//添加扣除理财管理费的交易记录
		investFeeDetail.addDealDetail(error);
		if(error.code < 0) {
			JPA.setRollbackOnly();
			
			return -1;
		}
		
		/* 添加CPS推广费 */
		User.rewardCPS(investUserId, manageFee, investId, error);
		
		if (error.code < 0) {
			JPA.setRollbackOnly();

			return -1;
		}
		
		//添加平台扣除理财管理费交易记录
		DealDetail.addPlatformDetail(DealType.INVEST_FEE, investId, investUserId, -1, DealType.ACCOUNT, manageFee, 1,
				"平台收取第"+investId+"号账单投资管理费", error);
		if(error.code < 0) {
			JPA.setRollbackOnly();
			
			return -1;
		}
		
		//如果投资用户账户资金没有遭到非法改动，那么就更新其篡改标志，否则不更新
		DataSafety dataTamperproof = new DataSafety();
		dataTamperproof.updateSignWithLock(investUserId, error);
		
		if(error.code < 0){
			error.code = -1;
			error.msg = "数据出现异常，导致付款失败";
		}
		
		Map<String, Object> userMap = new HashMap<String,Object>();
		
		String userSql = "select new Map(name as name, email as eamil, mobile as mobile) from t_users where id = ? ";
		
		try {
			userMap = t_users.find(userSql, investUserId).first();
		}catch(Exception e) {
			e.printStackTrace();
			Logger.info("根据用户id查询时："+e.getMessage());
			error.code = -1;
			error.msg = "查询用户名失败";
			
			return error.code;
		}
		
		String bidSql = "select title from t_bids where id = ? ";
		String title = null;
		
		try {
			title = t_bids.find(bidSql, bidId).first();
		}catch(Exception e) {
			e.printStackTrace();
			Logger.info("根据用户id查询时："+e.getMessage());
			error.code = -1;
			error.msg = "查询用户名失败";
			
			return error.code;
		}
		
		String userName = (String)userMap.get("name");
		String userEamil = (String)userMap.get("eamil");
		String userMobile = (String)userMap.get("mobile");
		
		//发送站内信 尊敬的username:\n 您投资的借款title,第repayPeriod期还款已经完成.<br/>paymentModeStr本期应得总额：
		//￥recivedSum,其中本金部分为：hasP元,利息部分：hasI元,实得逾期罚息：hasLFI元<br/>扣除投资管理费：
		//￥mFee元<br/>实得总额：￥msFee元
		TemplateStation station = new TemplateStation();
		station.id = Templets.M_INVEST_RECEIVE;
		
		if(station.status) {
			
			String mContent = station.content.replace("userName",userName);
			mContent = mContent.replace("title",title);
			mContent = mContent.replace("repayPeriod",period+"");
			mContent = mContent.replace("recivedSum", DataUtil.formatString(shouldReceiveAmount));
			mContent = mContent.replace("hasP",  DataUtil.formatString(receiveCorpus));
			mContent = mContent.replace("hasI",  DataUtil.formatString(receiveInterest));
			mContent = mContent.replace("hasLFI",  DataUtil.formatString(overDueFine));
			mContent = mContent.replace("mFee",  DataUtil.formatString(manageFee)+"");
			mContent = mContent.replace("msFee",  DataUtil.formatString(amount)+"");
			
			StationLetter letter = new StationLetter();
			letter.senderSupervisorId = 1;
			letter.receiverUserId = investUserId;
			letter.title = station.title;
			letter.content = mContent;
			 
			letter.sendToUserBySupervisor(error);
		}
		
		//发送邮件 尊敬的username:\n 您投资的借款title,第repayPeriod期还款已经完成.<br/>paymentModeStr本期应得总额：
		//￥recivedSum,其中本金部分为：hasP元,利息部分：hasI元,实得逾期罚息：hasLFI元<br/>扣除投资管理费：
		//￥mFee元<br/>实得总额：￥msFee元
		TemplateEmail email = new TemplateEmail();
		email.id = Templets.E_INVEST_RECEIVE;
		
		if(email.status) {
			String eContent = email.content.replace("userName", userName);
			eContent = eContent.replace("title",title);
			eContent = eContent.replace("repayPeriod",period+"");
			eContent = eContent.replace("recivedSum", DataUtil.formatString(shouldReceiveAmount));
			eContent = eContent.replace("hasP",  DataUtil.formatString(receiveCorpus));           
			eContent = eContent.replace("hasI",  DataUtil.formatString(receiveInterest));         
			eContent = eContent.replace("hasLFI",  DataUtil.formatString(overDueFine));           
			eContent = eContent.replace("mFee",  DataUtil.formatString(manageFee)+"");            
			eContent = eContent.replace("msFee",  DataUtil.formatString(amount)+"");              
			email.addEmailTask(userEamil, email.title, eContent);
		}
		
		//尊敬的userName: 投资的编号bidId借款标repayPeriod期已成功还款，回款金额￥recivedSum元，扣除管理费￥mFee元，实得总额￥msFee元
		TemplateSms sms = new TemplateSms();
		sms.id = Templets.S_RECOVER_ADVANCE_SUCCESS;
		BackstageSet backstageSet = BackstageSet.getCurrentBackstageSet();
		if(sms.status && StringUtils.isNotBlank(userMobile)) {
			String sContent = sms.content.replace("userName", userName);
			sContent = sContent.replace("bidId",backstageSet.loanNumber+bidId);
			sContent = sContent.replace("repayPeriod",period+"");
			sContent = sContent.replace("recivedSum", DataUtil.formatString(shouldReceiveAmount));
			sContent = sContent.replace("mFee",  DataUtil.formatString(manageFee)+"");            
			sContent = sContent.replace("msFee",  DataUtil.formatString(amount)+"");              
			TemplateSms.addSmsTask(userMobile, sContent);
		}
		
		error.code = 0;
		error.msg = "付款成功";
		
		return error.code;
 	}
 	
 	/**
 	 * 还款
 	 * @param userId 借款人id
 	 * @param error
 	 * @return
 	 */
 	public Map<String, List<Map<String, Object>>> repayment(long userId, ErrorInfo error) {
 		List<Map<String, Object>> list = new ArrayList<Map<String,Object>>();
 		Map<String, Object> mapp = new HashMap<String, Object>();
 		Map<String, Object> result = new HashMap<String, Object>();
 		error.clear();
 		
		double repaymentCorpus = 0;// 借款账单本期要付的本金
		double repaymentInterest = 0;// 借款账单本期要付的利息
		double repayOverdueFine = 0; //逾期管理费
		int mark = 0;// 是否逾期的标记
		int status = 0;// 账单的还款状态
		int period = 0;
		
		double balance = queryBalance(userId, error);//借款用户可用余额
		
		//1.查询本期借款账单的账单数据
		String sql = "select new Map(user.ips_acct_no as ips_acct_no,user.mobile as mobile,bid.id as bid_id,bid.bid_no as bid_no,bill.overdue_mark as overdue_mark, bill.repayment_corpus as " +
				"repayment_corpus, bill.repayment_interest as repayment_interest, bill.overdue_fine as overdue_fine," +
				" bill.status as status, bill.periods as period ,bill.repayment_time as repayment_time) from t_bills as bill,t_bids as bid, t_users as user where bill.bid_id = bid.id and bid.user_id = user.id and bill.id = ?";
		try {
			result = t_bills.find(sql, this._id).first(); 
		} catch(Exception e) {
			e.printStackTrace();
			Logger.info("查询借款账单信息时："+e.getMessage());
			error.code = -1;
			error.msg = "数据库异常，导致查询借款账单信息失败";
			JPA.setRollbackOnly();
			
			
			return null;
		}
		
		mark = (Integer)result.get("overdue_mark");
		repaymentCorpus = (Double)result.get("repayment_corpus");
		repaymentInterest = (Double)result.get("repayment_interest");
		status = (Integer)result.get("status");
		repayOverdueFine = (Double)result.get("overdue_fine");
		period = (Integer)result.get("period");
		
		for(Entry<String, Object> entry : result.entrySet()) {
			mapp.put(entry.getKey(), entry.getValue()==null?"":entry.getValue()+"");
		}
		
		//2.从投资记录表查询有哪些用户投资了这个借款标
		String sql3 = "select new Map(invest.id as id, invest.invest_id as investId, invest.status as invest_status, user.ips_acct_no as ipsAcctNo, user.mobile as mobile, invest.mer_bill_no as merBillNo, invest.receive_corpus as receive_corpus, invest.receive_interest " +
				"as receive_interest, invest.real_receive_interest as real_receive_interest , invest.user_id as user_id, invest.overdue_fine as overdue_fine) "
				+ "from t_bill_invests as invest, t_users as user where invest.user_id = user.id and invest.bid_id = ? and invest.periods = ? and invest.status not in (?,?,?,?)";
		try {
			list = t_bill_invests.find(sql3, this.bidId,this.periods,Constants.FOR_DEBT_MARK, Constants.NORMAL_RECEIVABLES, 
					Constants.ADVANCE_PRINCIIPAL_RECEIVABLES, Constants.OVERDUE_RECEIVABLES).fetch();
			
		} catch(Exception e) {
			e.printStackTrace();
			Logger.info("查询投资账单信息时："+e.getMessage());
			error.code = -3;
			error.msg = "数据库异常，导致查询投资账单信息失败";
			JPA.setRollbackOnly();
			
			return null;
		}
		if (mark == Constants.BILL_NO_OVERDUE) {//如果未标记逾期，则判断为正常还款
			
			//本次还款金额

			double payment =repaymentCorpus;
			//本次要还的利息
			double rateToInvest = 0;
		    //判断是否是提前还款
			String repay_time = result.get("repayment_time").toString();
			Date date1 = DateUtil.strToYYMMDDDate(DateUtil.dateToString(new Date())); 
			Date date2 = DateUtil.strToYYMMDDDate(repay_time); 
			boolean flag = date1.before(date2);
			
			if(flag){
				sql = "select ifnull(sum(real_receive_interest),0) + 0.0  from t_bill_invests where bid_id =?";
				double d1 = t_user_details.find(sql,result.get("bid_id")).first();
			    rateToInvest = (Double)result.get("repayment_interest") - d1;
				
			}
		
			payment = repaymentCorpus + rateToInvest;
			
			//还款后金额
			double lastBalance = Arith.sub(balance, payment);
			
			//判断用户账户是否被恶意篡改
			DataSafety dataTamperproof = new DataSafety();
			dataTamperproof.id = userId;
			boolean isChanged = dataTamperproof.signCheck(error);
			
			if(isChanged == false){
				JPA.setRollbackOnly();
				return null;
			}

			//1.判断借款人余额是否足够
			if (lastBalance < 0) {
				error.code = Constants.BALANCE_NOT_ENOUGH;
				error.msg = "余额不足，暂时不能还款，请及时充值";
				
				return null;
			}
			
			//2.判断该借款是否已经还款，防止重复提交
			if(this.status == Constants.NORMAL_REPAYMENT || this.status == Constants.OVERDUE_PATMENT){
				error.code = -1;
				error.msg = "本期账单已还款";
				
				return null;
			}
			
			//3.判断还款的借款标有没有债权在转让，如果有，将其状态回归原态,并解冻竞拍者资金
			int resulta = Debt.judgeHasBidTransfer(this.bidId);
			if(resulta < 0){
				error.code = -7;
				error.msg = "还款出现异常，导致还款失败";
				
				return null;
			}
		
			if (null == list) {
				error.code = -4;
				error.msg = "还款出现异常，导致还款失败";
				
				return null;
			}			
			this.normalPayment(this.bidId, userId, repaymentCorpus ,repaymentInterest, flag,balance, list, period, error);
			
		} else {//否则判断为逾期还款
			double payment = repaymentCorpus  + repayOverdueFine;// 总共要还款的金额
			double lastBalance = Arith.sub(balance, payment);
			
			//判断用户账户是否被恶意篡改
			DataSafety dataTamperproof = new DataSafety();
			dataTamperproof.id = userId;
			boolean isChanged = dataTamperproof.signCheck(error);
			
			if(isChanged == false){
				return null;
			}

			//1.判断借款人余额是否足够
			if (lastBalance < 0) {
				error.code = Constants.BALANCE_NOT_ENOUGH;
				error.msg = "余额不足，暂时不能还款，请及时充值";
				
				return null;
			}
			
			//2.判断该借款是否已经还款，防止重复提交
			if(this.status == Constants.NORMAL_REPAYMENT || this.status == Constants.OVERDUE_PATMENT){
				error.code = -1;
				error.msg = "本期账单已还款";
				
				return null;
			}
			
			//本金垫付还款不用流拍
			if (status != Constants.ADVANCE_PRINCIIPAL_REPAYMENT) {
				//3.判断还款的借款标有没有债权在转让，如果有，将其状态回归原态,并解冻竞拍者资金
				int resulta = Debt.judgeHasBidTransfer(bidId);
				if(resulta < 0){
					error.code = -7;
					error.msg = "还款出现异常，导致还款失败";
					
					return null;
				}
			}
			
			if (null == list) {
				error.code = -3;
				error.msg = "还款出现异常，导致还款失败";
				JPA.setRollbackOnly();
				
				return null;
			}
			this.overduePayment(this.bidId, userId, repaymentCorpus, repaymentInterest,balance, list, status, repayOverdueFine, period, error);
			
		}
		
		error.code = 0;
		return null;
 	}
 	
 	
 	//体验金还款
 	public Map<String, List<Map<String, Object>>> repayment2(long userId, ErrorInfo error) {
 		List<Map<String, Object>> list = new ArrayList<Map<String,Object>>();
 		Map<String, Object> mapp = new HashMap<String, Object>();
 		Map<String, Object> result = new HashMap<String, Object>();
 		error.clear();
 		
		double repaymentCorpus = 0;// 借款账单本期要付的本金
		double repaymentInterest = 0;// 借款账单本期要付的利息
		double repayOverdueFine = 0; //逾期管理费
		int mark = 0;// 是否逾期的标记
		int status = 0;// 账单的还款状态
		int period = 0;
		
		double balance = queryBalance(userId, error);//借款用户可用余额
		
		//1.查询本期借款账单的账单数据
		String sql = "select new Map(user.ips_acct_no as ips_acct_no,user.mobile as mobile,bid.id as bid_id,bid.bid_no as bid_no,bill.overdue_mark as overdue_mark, bill.repayment_corpus as " +
				"repayment_corpus, bill.repayment_interest as repayment_interest, bill.overdue_fine as overdue_fine," +
				" bill.status as status, bill.periods as period ,bill.repayment_time as repayment_time) from t_bills as bill,t_bids as bid, t_users as user where bill.bid_id = bid.id and bid.user_id = user.id and bill.id = ?";
		try {
			result = t_bills.find(sql, this._id).first(); 
		} catch(Exception e) {
			e.printStackTrace();
			Logger.info("查询借款账单信息时："+e.getMessage());
			error.code = -1;
			error.msg = "数据库异常，导致查询借款账单信息失败";
			JPA.setRollbackOnly();
			
			
			return null;
		}
		
		mark = (Integer)result.get("overdue_mark");
		repaymentCorpus = (Double)result.get("repayment_corpus");
		repaymentInterest = (Double)result.get("repayment_interest");
		status = (Integer)result.get("status");
		repayOverdueFine = (Double)result.get("overdue_fine");
		period = (Integer)result.get("period");
		
		for(Entry<String, Object> entry : result.entrySet()) {
			mapp.put(entry.getKey(), entry.getValue()==null?"":entry.getValue()+"");
		}
		
		//2.从投资记录表查询有哪些用户投资了这个借款标
		String sql3 = "select new Map(invest.id as id, invest.invest_id as investId, invest.status as invest_status, user.ips_acct_no as ipsAcctNo, user.mobile as mobile, invest.mer_bill_no as merBillNo, invest.receive_corpus as receive_corpus, invest.receive_interest " +
				"as receive_interest, invest.real_receive_interest as real_receive_interest , invest.user_id as user_id, invest.overdue_fine as overdue_fine) "
				+ "from t_bill_invests as invest, t_users as user where invest.user_id = user.id and invest.bid_id = ? and invest.periods = ? and invest.status not in (?,?,?,?)";
		try {
			list = t_bill_invests.find(sql3, this.bidId,this.periods,Constants.FOR_DEBT_MARK, Constants.NORMAL_RECEIVABLES, 
					Constants.ADVANCE_PRINCIIPAL_RECEIVABLES, Constants.OVERDUE_RECEIVABLES).fetch();
			
		} catch(Exception e) {
			e.printStackTrace();
			Logger.info("查询投资账单信息时："+e.getMessage());
			error.code = -3;
			error.msg = "数据库异常，导致查询投资账单信息失败";
			JPA.setRollbackOnly();
			
			return null;
		}
		if (mark == Constants.BILL_NO_OVERDUE) {//如果未标记逾期，则判断为正常还款
			
			//本次还款金额

			double payment =repaymentCorpus;
			//本次要还的利息
			double rateToInvest = 0;
		    //判断是否是提前还款
			String repay_time = result.get("repayment_time").toString();
			Date date1 = DateUtil.strToYYMMDDDate(DateUtil.dateToString(new Date())); 
			Date date2 = DateUtil.strToYYMMDDDate(repay_time); 
			boolean flag = date1.before(date2);
			
			if(flag){
				sql = "select ifnull(sum(real_receive_interest),0) + 0.0  from t_bill_invests where bid_id =?";
				double d1 = t_user_details.find(sql,result.get("bid_id")).first();
			    rateToInvest = (Double)result.get("repayment_interest") - d1;
				
			}
		
			payment = repaymentCorpus + rateToInvest;
			
			//还款后金额
			//double lastBalance = Arith.sub(balance, payment);
			double lastBalance = balance;
			//判断用户账户是否被恶意篡改
			DataSafety dataTamperproof = new DataSafety();
			dataTamperproof.id = userId;
			boolean isChanged = dataTamperproof.signCheck(error);
			
			if(isChanged == false){
				JPA.setRollbackOnly();
				return null;
			}

			//1.判断借款人余额是否足够
			if (lastBalance < 0) {
				error.code = Constants.BALANCE_NOT_ENOUGH;
				error.msg = "余额不足，暂时不能还款，请及时充值";
				
				return null;
			}
			
			//2.判断该借款是否已经还款，防止重复提交
			if(this.status == Constants.NORMAL_REPAYMENT || this.status == Constants.OVERDUE_PATMENT){
				error.code = -1;
				error.msg = "本期账单已还款";
				
				return null;
			}
			
			//3.判断还款的借款标有没有债权在转让，如果有，将其状态回归原态,并解冻竞拍者资金
			int resulta = Debt.judgeHasBidTransfer(this.bidId);
			if(resulta < 0){
				error.code = -7;
				error.msg = "还款出现异常，导致还款失败";
				
				return null;
			}
		
			if (null == list) {
				error.code = -4;
				error.msg = "还款出现异常，导致还款失败";
				
				return null;
			}			
			this.normalPayment2(this.bidId, userId, repaymentCorpus ,repaymentInterest, flag,balance, list, period, error);
			
		} else {//否则判断为逾期还款
			double payment = repaymentCorpus  + repayOverdueFine;// 总共要还款的金额
			//double lastBalance = Arith.sub(balance, payment);
			double lastBalance = balance;
			//判断用户账户是否被恶意篡改
			DataSafety dataTamperproof = new DataSafety();
			dataTamperproof.id = userId;
			boolean isChanged = dataTamperproof.signCheck(error);
			
			if(isChanged == false){
				return null;
			}

			//1.判断借款人余额是否足够
			if (lastBalance < 0) {
				error.code = Constants.BALANCE_NOT_ENOUGH;
				error.msg = "余额不足，暂时不能还款，请及时充值";
				
				return null;
			}
			
			//2.判断该借款是否已经还款，防止重复提交
			if(this.status == Constants.NORMAL_REPAYMENT || this.status == Constants.OVERDUE_PATMENT){
				error.code = -1;
				error.msg = "本期账单已还款";
				
				return null;
			}
			
			//本金垫付还款不用流拍
			if (status != Constants.ADVANCE_PRINCIIPAL_REPAYMENT) {
				//3.判断还款的借款标有没有债权在转让，如果有，将其状态回归原态,并解冻竞拍者资金
				int resulta = Debt.judgeHasBidTransfer(bidId);
				if(resulta < 0){
					error.code = -7;
					error.msg = "还款出现异常，导致还款失败";
					
					return null;
				}
			}
			
			if (null == list) {
				error.code = -3;
				error.msg = "还款出现异常，导致还款失败";
				JPA.setRollbackOnly();
				
				return null;
			}
			this.overduePayment2(this.bidId, userId, repaymentCorpus, repaymentInterest,balance, list, status, repayOverdueFine, period, error);
			
		}
		
		error.code = 0;
		return null;
 	}
 	
 
 	
 	
 	public int normalPayment(long bidId, long userId, double repaymentCorpus, double repaymentInterest, boolean flag,double balance, 
		    List<Map<String, Object>> list, int period, ErrorInfo error){
 		error.clear();
 		
 		double managementRate = Bid.queryInvestRate(bidId);
 		
 		if(managementRate != 0){
 			managementRate = managementRate / 100;
		}
 		
 		
 		
 		double payment = repaymentCorpus;
		EntityManager em = JPA.em();
		int rows = 0;
		
		BackstageSet backstageSet = BackstageSet.getCurrentBackstageSet();
		
		String bidTitleSql = "select title from t_bids where id = ? ";
		String title = null;
		
		try {
			title = t_bids.find(bidTitleSql, bidId).first();
		}catch(Exception e) {
			e.printStackTrace();
			Logger.info("根据标id查询时："+e.getMessage());
			error.code = -1;
			error.msg = "查询用户名失败";
			
			return error.code;
		}
		
		//判断系统应付账单设置（0为自动付款，1为手动付款）
		if(backstageSet.repayType.equalsIgnoreCase(Constants.AUTO_PAYMENT) || Constants.IPS_ENABLE){

			//5.遍历本期的投资账单，计算并操作对应的还款金额到投资人账上
			for (Map<String, Object> map : list) {
				JSONObject json = JSONObject.fromObject(map);
				long investId = json.getLong("investId");//投资id
				long investBillId = json.getLong("id");//每个投资人的投资账单id
				long investUserId = json.getLong("user_id");//投资人id
				double receiveCorpus = json.getDouble("receive_corpus");//投资本金
				double receiveInterest = json.getDouble("receive_interest");//投资利息
				long invest_status = json.getLong("invest_status");//投资账单状态
				double realReceiveInterest = json.getDouble("real_receive_interest");//投资人实际获得利息
				double manageFee = BillInvests.getInvestManagerFee(receiveInterest, managementRate, investUserId);  //投资管理费
		
				double receive = Arith.round(receiveCorpus + receiveInterest, 2);//计算投资人将获得的收益
			
				//查看投资用户是否受到非法资金改动
				DataSafety investDataTamperproof = new DataSafety();
				investDataTamperproof.id = investUserId;
				boolean investIsChange = investDataTamperproof.signCheck(error);
				
				double receive_rate =0;	     //剩余利息
				if(flag){//提前还款   返给理财人剩余利息
					receive_rate =receiveInterest - realReceiveInterest ;
				}
				
				//7. 修改投资账单的收款情况
		 		String updateSql = "update t_bill_invests set status = ?, real_receive_time = ?, real_receive_corpus = ? ,real_receive_interest = real_receive_interest + ? " +
		 				"  where user_id = ? and  bid_id = ?  and  periods = ?  and  status not in(?,?)  and invest_id = ?";
		 		
		        Query query = em.createQuery(updateSql).setParameter(1, Constants.NORMAL_RECEIVABLES).setParameter(2, DateUtil.currentDate())
		        .setParameter(3, receiveCorpus) .setParameter(4, receive_rate).setParameter(5, investUserId).
		        setParameter(6, this.bidId).setParameter(7, this.periods).setParameter(8, Constants.NORMAL_RECEIVABLES).setParameter(9, Constants.FOR_DEBT_MARK).setParameter(10, investId);
		        
		        try {
		        	rows = query.executeUpdate();
				} catch(Exception e) {
					e.printStackTrace();
					Logger.info("修改投资账单的收款情况时："+e.getMessage());
					error.code = -6;
					error.msg = "还款出现异常，导致还款失败";
					JPA.setRollbackOnly();
					
					return error.code;
				}
				
				if(rows == 0){
					error.code = -1;
					error.msg = "还款出现异常，导致还款失败";
					JPA.setRollbackOnly();
					
					return error.code;
				}
				
				//6.投资人余额增加获取的投资本金和利息
				if(invest_status != Constants.ONE){
					
					String balanceSql = "update t_users set balance = balance + ? where id = ?";
					//投资本金减投资管理费
					Query BalanceUpdate = em.createQuery(balanceSql).setParameter(1, receiveCorpus).setParameter(2, investUserId);
					
					try {
						rows = BalanceUpdate.executeUpdate();
						
					} catch(Exception e) {
						e.printStackTrace();
						Logger.info("返回每个投资用户每期获得投资本金时："+e.getMessage());
						error.code = -5;
						error.msg = "数据库异常，导致还款失败";
						JPA.setRollbackOnly();
						
						return error.code;
					}
					
					if(rows == 0){
						error.code = -1;
						error.msg = "还款出现异常，导致还款失败";
						JPA.setRollbackOnly();
						
						return error.code;
					}
				
				
				
				Map<String, Double> investForDetail = DealDetail.queryUserFund(investUserId, error);
				if(error.code < 0 || investForDetail == null) {
					JPA.setRollbackOnly();
					
					return -1;
				}
				
				double investFreeze = investForDetail.get("freeze");
				double investReceiveAmount = investForDetail.get("receive_amount");
				
				Double investerBalance = this.queryBalance(investUserId, error);
				
				DealDetail investDetail = new DealDetail(investUserId, DealType.NOMAL_RECEIVE, receiveCorpus,
						investBillId, investerBalance, investFreeze, investReceiveAmount, "正常收款获取第"+this._id+"号账单投资金额");
				
				//添加正常收款的交易记录
				investDetail.addDealDetail(error);
				if(error.code < 0) {
					JPA.setRollbackOnly();
					
					return -1;
				}
				
				//6.投资人可用余额获得剩余利息
				if(receive_rate > 0){
					String balanceSql1 = "update t_users set balance = balance + ? where id = ? ";
					
					Query BalanceUpdate1 = em.createQuery(balanceSql1).setParameter(1, receive_rate).setParameter(2, investUserId);
					
					try {
						rows = BalanceUpdate1.executeUpdate();
						
					} catch(Exception e) {
						e.printStackTrace();
						Logger.info("扣取投资管理费时："+e.getMessage());
						error.code = -5;
						error.msg = "数据库异常，导致还款失败";
						JPA.setRollbackOnly();
						
						return error.code;
					}
					
					if(rows == 0){
						error.code = -1;
						error.msg = "还款出现异常，导致还款失败";
						JPA.setRollbackOnly();
						
						return error.code;
					}
					
					
					//本次查询用户可用余额是获取最新状态值，避免从缓存里拿数据
					Double investerBalance2 = this.queryBalance(investUserId, error);
					
					DealDetail investFeeDetail = new DealDetail(investUserId, DealType.NOMAL_RECEIVE, receive_rate,
							investBillId, investerBalance2, investFreeze, investReceiveAmount, "正常收款获取第"+this._id+"号账单剩余利息");
					
					//添加扣交易记录
					investFeeDetail.addDealDetail(error);
					if(error.code < 0) {
						JPA.setRollbackOnly();
						
						return -1;
					}
				}
				
				
				//6.投资人可用余额减去投资管理费
				String balanceSql2 = "update t_users set balance = balance - ? where id = ? and balance >= ?";
				
				Query  BalanceUpdate2 = em.createQuery(balanceSql2).setParameter(1, manageFee).setParameter(2, investUserId).setParameter(3, manageFee);
				
				try {
					rows = BalanceUpdate2.executeUpdate();
					
				} catch(Exception e) {
					e.printStackTrace();
					Logger.info("扣取投资管理费时："+e.getMessage());
					error.code = -5;
					error.msg = "数据库异常，导致还款失败";
					JPA.setRollbackOnly();
					
					return error.code;
				}
				
				if(rows == 0){
					error.code = -1;
					error.msg = "还款出现异常，导致还款失败";
					JPA.setRollbackOnly();
					
					return error.code;
				}
				
				
				//本次查询用户可用余额是获取最新状态值，避免从缓存里拿数据
				Double investerBalance3 = this.queryBalance(investUserId, error);
				
				DealDetail investFeeDetail2 = new DealDetail(investUserId, DealType.CHARGE_INVEST_FEE, manageFee,
						investBillId, investerBalance3, investFreeze, investReceiveAmount, "扣除第"+this._id+"号账单理财管理费");
				
				//添加扣除理财管理费的交易记录
				investFeeDetail2.addDealDetail(error);
				if(error.code < 0) {
					JPA.setRollbackOnly();
					
					return -1;
				}
				
				
				
				
				Map<String, Object> userMap = new HashMap<String,Object>();
				
				String userSql = "select new Map(name as name, email as eamil, mobile as mobile) from t_users where id = ? ";
				
				try {
					userMap = t_users.find(userSql, investUserId).first();
				}catch(Exception e) {
					e.printStackTrace();
					Logger.info("根据用户id查询时："+e.getMessage());
					error.code = -1;
					error.msg = "查询用户名失败";
					
					return error.code;
				}
				
				String userName = (String)userMap.get("name");
				String userEamil = (String)userMap.get("eamil");
				String userMobile = (String)userMap.get("mobile");
				
				//发送站内信 尊敬的username:\n 您投资的借款title,第repayPeriod期还款已经完成.<br/>paymentModeStr本期应得总额：
				//￥recivedSum,其中本金部分为：hasP元,利息部分：hasI元,实得逾期罚息：hasLFI元<br/>扣除投资管理费：
				//￥mFee元<br/>实得总额：￥msFee元
				TemplateStation station = new TemplateStation();
				station.id = Templets.M_INVEST_RECEIVE;
				
				if(station.status) {
					String mContent = station.content.replace("userName",userName);
					mContent = mContent.replace("title",title);
					mContent = mContent.replace("repayPeriod",this.periods+"");
					mContent = mContent.replace("recivedSum",  DataUtil.formatString(receive));
					mContent = mContent.replace("hasP",  DataUtil.formatString(receiveCorpus));
					mContent = mContent.replace("hasI",  DataUtil.formatString(receiveInterest));
					mContent = mContent.replace("bchasY",  DataUtil.formatString(receive_rate));
					mContent = mContent.replace("hasLFI", "0.00");
					mContent = mContent.replace("mFee",  DataUtil.formatString(manageFee));
					mContent = mContent.replace("msFee",  DataUtil.formatString(Arith.round(receive - manageFee, 2)));
					
					StationLetter letter = new StationLetter();
					letter.senderSupervisorId = 1;
					letter.receiverUserId = investUserId;
					letter.title = station.title;
					letter.content = mContent;
					 
					letter.sendToUserBySupervisor(error);
				}
				
				//发送邮件 尊敬的username:\n [date] 您在晓风安全网贷系统6对标的【title】还款了￥needSum 元
				TemplateEmail email = new TemplateEmail();
				email.id = Templets.E_INVEST_RECEIVE;
				
				if(email.status) {
					String eContent = email.content.replace("userName", userName);
					eContent = eContent.replace("title",title);
					eContent = eContent.replace("repayPeriod",this.periods+"");
					eContent = eContent.replace("recivedSum",  DataUtil.formatString(receive));                        
					eContent = eContent.replace("hasP",  DataUtil.formatString(receiveCorpus));                        
					eContent = eContent.replace("hasI",  DataUtil.formatString(receiveInterest));                      
					eContent = eContent.replace("hasLFI", "0.00");                                                     
					eContent = eContent.replace("mFee",  DataUtil.formatString(manageFee));                            
					eContent = eContent.replace("msFee",  DataUtil.formatString(Arith.round(receive - manageFee, 2))); 
					email.addEmailTask(userEamil, email.title, eContent);
				}
				
				//尊敬的userName: 投资的编号bidId借款标repayPeriod期已成功还款，回款金额￥recivedSum元，扣除管理费￥mFee元，实得总额￥msFee元
				TemplateSms sms = new TemplateSms();
				sms.id = Templets.S_RECOVER_ADVANCE_SUCCESS;
				if(sms.status && StringUtils.isNotBlank(userMobile)) {
					String sContent = sms.content.replace("userName", userName);
					sContent = sContent.replace("title",title);
					sContent = sContent.replace("repayPeriod",this.periods+"");
					sContent = sContent.replace("recivedSum",  DataUtil.formatString(receive)); 
					sContent = sContent.replace("hasP",  DataUtil.formatString(receiveCorpus));
					sContent = sContent.replace("hasI",  DataUtil.formatString(receiveInterest));
					sContent = sContent.replace("bchasY",  DataUtil.formatString(receive_rate));
					sContent = sContent.replace("hasLFI", "0.00");
					sContent = sContent.replace("mFee",  DataUtil.formatString(manageFee));                            
					sContent = sContent.replace("msFee",  DataUtil.formatString(Arith.round(receive - manageFee, 2)));                                                   
					TemplateSms.addSmsTask(userMobile, sContent);
				}
				
				//添加平台扣除理财管理费交易记录
				DealDetail.addPlatformDetail(DealType.INVEST_FEE, investBillId, investUserId, -1, DealType.ACCOUNT,
						manageFee, 1, "平台收取"+this._id+"投资管理费", error);
				if(error.code < 0) {
					JPA.setRollbackOnly();
					
					return -1;
				}
				
				/* 添加CPS推广费 */
				User.rewardCPS(investUserId, manageFee, investBillId, error);
				
				if (error.code < 0) {
					JPA.setRollbackOnly();

					return error.code;
				}
				
				//如果投资用户账户资金没有遭到非法改动，那么就更新其篡改标志，否则不更新
				if(investIsChange){
					investDataTamperproof.updateSignWithLock(investUserId, error);
				}
				
		     }else{
				
		 		DealDetail dealDetail = new DealDetail();
			
				/* 添加平台交易记录 */
				dealDetail.addPlatformDetail(DealType.ADD_PLATFORM_REGULAR,userId, userId, -1, 
						DealType.ACCOUNT, receiveCorpus, 1,
						"返还垫付给投资者赎回金额", error);
				
				if (error.code < 0) {
					JPA.setRollbackOnly();
					error.msg = "借款用户返还垫付给投资者赎回金额失败";
					return -1;
				}
				//用户事件添加
				DealDetail.userEvent(userId, UserEvent.ROLLBACK_SHUHUI, "返还赎回金额成功", error);
				
	    	}
    	 }
		}
		//手动付款模式
		else{
			String updateInvestSql = "update t_bill_invests set status = ? where bid_id = ? and periods = ? and status not in(?,?,?,?,?)";
			
	        Query updateInvest = em.createQuery(updateInvestSql).setParameter(1, Constants.FOR_PAY).setParameter(2, bidId)
	        .setParameter(3, period).setParameter(4, Constants.FOR_PAY).setParameter(5, Constants.NORMAL_RECEIVABLES)
	        .setParameter(6, Constants.ADVANCE_PRINCIIPAL_RECEIVABLES).setParameter(7, Constants.OVERDUE_RECEIVABLES).setParameter(8, Constants.FOR_DEBT_MARK);
			
			try {
				rows = updateInvest.executeUpdate();
			} catch(Exception e) {
				e.printStackTrace();
				Logger.info("修改投资账单状态时："+e.getMessage());
				error.code = -3;
				error.msg = "数据库异常，导致修改投资账单状态失败";
				JPA.setRollbackOnly();
				
				return error.code;
			}
		}
		
		//10.修改借款账单还款情况
		String updateBillSql = "update t_bills set status = ?, real_repayment_time = ?, real_repayment_corpus = ?, real_repayment_interest = ? where id = ? and status = ? ";
			
		Query updateBill = em.createQuery(updateBillSql).setParameter(1, Constants.NORMAL_REPAYMENT).setParameter(2, DateUtil.currentDate())
        .setParameter(3, repaymentCorpus).setParameter(4, repaymentInterest).setParameter(5, this._id).setParameter(6, Constants.NO_REPAYMENT);
		
		try {
			rows = updateBill.executeUpdate();
		} catch(Exception e) {
			e.printStackTrace();
			Logger.info("修改借款账单数据时："+e.getMessage());
			error.code = -3;
			error.msg = "还款出现异常，导致还款失败";
			JPA.setRollbackOnly();
			
			return error.code;
		}finally{
			double Userbalance = queryBalance(userId, error);//用户可用余额
			
			if(rows == 0){
				error.code = -1;
				error.msg = "还款出现异常，导致还款失败";
				JPA.setRollbackOnly();
				
				return error.code;
			}
			
			if(Userbalance < 0){
				error.code = -1;
				error.msg = "资金不足,请充值!";
				JPA.setRollbackOnly();
				
				return error.code;
			}
		}
		
		//扣除借款人还款本期的所需金额
		String userBalanceSql = "update t_users set balance = balance - ? where id = ? and balance >= ?";
		
		Query userBalance = em.createQuery(userBalanceSql).setParameter(1, payment).setParameter(2, userId).setParameter(3, payment);
		
		try {
			rows = userBalance.executeUpdate();
		} catch(Exception e) {
			e.printStackTrace();
			Logger.info("修改借款人账户金额时："+e.getMessage());
			error.code = -2;
			error.msg = "还款出现异常，导致还款失败";
			JPA.setRollbackOnly();
			
			return error.code;
		}
		
		if(rows == 0){
			error.code = -1;
			error.msg = "还款出现异常，导致还款失败";
			JPA.setRollbackOnly();
			
			return error.code;
		}
		
		//9.针对借款账单做交易记录和事件
		Map<String, Double> billForDetail2 = DealDetail.queryUserFund(userId, error);
		if(error.code < 0 || billForDetail2 == null) {
			JPA.setRollbackOnly();
			
			return -1;
		}
		
		double userFreeze3 = billForDetail2.get("freeze");
		double userReceiveAmount3 = billForDetail2.get("receive_amount");
			
		Double investerBalance10 = this.queryBalance(userId, error);
		
		DealDetail detail = new DealDetail(userId, DealType.CHARGE_NOMAL_PAY, payment,
				this._id, investerBalance10, userFreeze3, userReceiveAmount3, "第"+this._id+"号账单正常还款扣除还款金额");
		
		//添加正常还款的交易记录
		detail.addDealDetail(error);
		if(error.code < 0) {
			JPA.setRollbackOnly();
			
			return -1;
		}
		
		//正常还款获得信用积分
		int creditScore = detail.addCreditScore(userId, 2, 0, this._id, "第"+this._id+"号账单正常还款获得信用积分", error);
		if(error.code < 0) {
			JPA.setRollbackOnly();
			
			return -1;
		}
		
		//更新用户的信用等级
		/* 
		 * v.7.2.7 不及时更新用户信用等级，每天凌晨统一更新
		User.updateCreditLevel(userId, error);
		if (error.code < 0) {
			JPA.setRollbackOnly();
			
			return -1;
		}
		*/
		
		//添加事件
		DealDetail.userEvent(userId, UserEvent.ADD_NOMAL_PAY, "第"+this._id+"号账单用户正常还款", error);
		if(error.code < 0) {
			JPA.setRollbackOnly();
			
			return -1;
		}
		
		Map<String, Object> userMap = new HashMap<String,Object>();
		
		String userSql = "select new Map(name as name, email as eamil, mobile as mobile) from t_users where id = ? ";
		
		try {
			userMap = t_users.find(userSql, userId).first();
		}catch(Exception e) {
			e.printStackTrace();
			Logger.info("根据用户id查询时："+e.getMessage());
			error.code = -1;
			error.msg = "查询用户名失败";
			JPA.setRollbackOnly();
			
			return error.code;
		}
		
		String userName = (String)userMap.get("name");
		String userEamil = (String)userMap.get("eamil");
		String userMobile = (String)userMap.get("mobile");
		
		//发送站内信  尊敬的username:\n [date] 您在晓风安全网贷系统6对标的【title】还款了￥needSum 元
		TemplateStation station = new TemplateStation();
		station.id = Templets.M_SUCCESS_PAY;
		
		if(station.status) {
			String mContent = station.content.replace("userName", userName);
			mContent = mContent.replace("date", DateUtil.dateToString(new Date()));
			mContent = mContent.replace("bidId",backstageSet.loanNumber+bidId);
			mContent = mContent.replace("repayPeriod",this.periods+"");
			mContent = mContent.replace("needSum",  DataUtil.formatString(payment+repaymentInterest));
			mContent = mContent.replace("rcp",  DataUtil.formatString(repaymentCorpus));
			mContent = mContent.replace("hasI",  DataUtil.formatString(repaymentInterest));
			mContent = mContent.replace("rodf",  "0.00");
			mContent = mContent.replace("creditScore",creditScore+"");
			
			StationLetter letter = new StationLetter();
			letter.senderSupervisorId = 1;
			letter.receiverUserId = userId;
			letter.title = station.title;
			letter.content = mContent;
			 
			letter.sendToUserBySupervisor(error);
		}
		
		//发送邮件 尊敬的username:\n [date] 您在晓风安全网贷系统6对标的【title】还款了￥needSum 元
		TemplateEmail email = new TemplateEmail();
		email.id = Templets.E_SUCCESS_PAY;
		
		if(email.status) {
			String eContent = email.content.replace("userName", userName);
			eContent = eContent.replace("date", DateUtil.dateToString(new Date()));
			eContent = eContent.replace("title",title);
			eContent = eContent.replace("needSum",payment+"");
			eContent = eContent.replace("creditScore",creditScore+"");
			email.addEmailTask(userEamil, email.title, eContent);
		}
		
		//发送短信  尊敬的userName: 你申请的编号bidId借款标repayPeriod期成功还款，还款金额￥needSum元，
		TemplateSms sms = new TemplateSms();
		sms.id = Templets.S_REPAY_SUCCESS;
		if(sms.status && StringUtils.isNotBlank(userMobile)) {
			String eContent = sms.content.replace("userName", userName);
			eContent = eContent.replace("bidId",backstageSet.loanNumber+bidId);
			eContent = eContent.replace("repayPeriod",this.periods+"");
			eContent = eContent.replace("needSum",DataUtil.formatString(payment+repaymentInterest));
			eContent = eContent.replace("rcp",  DataUtil.formatString(repaymentCorpus));
			eContent = eContent.replace("hasI",  DataUtil.formatString(repaymentInterest));
			eContent = eContent.replace("rodf",  "0.00");
			TemplateSms.addSmsTask(userMobile, eContent);
		}
		
		//11.判断这个借款标是否已还完款，若还完标记这个借款标为已还款完状态
		if(this.isEndPayment(bidId, error) == 0){
			String bidSql = "update t_bids set status = ?, last_repay_time = ? where id = ?";
		
            Query query = em.createQuery(bidSql).setParameter(1, Constants.BID_REPAYMENTS).setParameter(2, DateUtil.currentDate())
            .setParameter(3, bidId);
            
			try {
				rows = query.executeUpdate();;
			
			} catch(Exception e) {
				e.printStackTrace();
				Logger.info("修改借款标为已还款完状态时："+e.getMessage());
				error.code = -7;
				error.msg = "还款出现异常，导致还款失败";
				JPA.setRollbackOnly();
				
				return error.code;
			}
			
			if(rows == 0){
				error.code = -1;
				error.msg = "还款出现异常，导致还款失败";
				JPA.setRollbackOnly();
			
				return error.code;
			}
		}
		
		//12.更改当前用户在缓存变动的数据
		/*User user = User.currUser();
		
		if(user != null){
			user.balance = lastBalance;
			user.creditScore = user.creditScore + creditScore;
			user.setCurrUser(user);
		}*/
		
		//13.还款完毕后重新生成新的数据库防篡改标志
		DataSafety dataTamperproof = new DataSafety();
		dataTamperproof.updateSignWithLock(userId, error);
		
		error.msg = "还款成功";
		error.code = 0;
		return error.code;
 	}
 	
 	
 	/**
 	 * 体验金还款
 	 * @param bidId
 	 * @param userId
 	 * @param repaymentCorpus
 	 * @param repaymentInterest
 	 * @param flag
 	 * @param balance
 	 * @param list
 	 * @param period
 	 * @param error
 	 * @return
 	 */
 	public int normalPayment2(long bidId, long userId, double repaymentCorpus, double repaymentInterest, boolean flag,double balance, 
		    List<Map<String, Object>> list, int period, ErrorInfo error){
 		error.clear();
 		
 		double managementRate = Bid.queryInvestRate(bidId);
 		
 		if(managementRate != 0){
 			managementRate = managementRate / 100;
		}
 		
// 		
 		
 		double payment = repaymentCorpus;
		EntityManager em = JPA.em();
		int rows = 0;
		
		BackstageSet backstageSet = BackstageSet.getCurrentBackstageSet();
		
		String bidTitleSql = "select title from t_bids where id = ? ";
		String title = null;
		
		try {
			title = t_bids.find(bidTitleSql, bidId).first();
		}catch(Exception e) {
			e.printStackTrace();
			Logger.info("根据标id查询时："+e.getMessage());
			error.code = -1;
			error.msg = "查询用户名失败";
			
			return error.code;
		}
		
		//判断系统应付账单设置（0为自动付款，1为手动付款）
		if(backstageSet.repayType.equalsIgnoreCase(Constants.AUTO_PAYMENT) || Constants.IPS_ENABLE){

			//5.遍历本期的投资账单，计算并操作对应的还款金额到投资人账上
			for (Map<String, Object> map : list) {
				JSONObject json = JSONObject.fromObject(map);
				long investId = json.getLong("investId");//投资id
				long investBillId = json.getLong("id");//每个投资人的投资账单id
				long investUserId = json.getLong("user_id");//投资人id
				double receiveCorpus = json.getDouble("receive_corpus");//投资本金
				double receiveInterest = json.getDouble("receive_interest");//投资利息
				long invest_status = json.getLong("invest_status");//投资账单状态
				double realReceiveInterest = json.getDouble("real_receive_interest");//投资人实际获得利息
				double manageFee = BillInvests.getInvestManagerFee(receiveInterest, managementRate, investUserId);  //投资管理费
		
				double receive = Arith.round(receiveCorpus+receiveInterest, 2);//计算投资人将获得的收益
			
				//查看投资用户是否受到非法资金改动
				DataSafety investDataTamperproof = new DataSafety();
				investDataTamperproof.id = investUserId;
				boolean investIsChange = investDataTamperproof.signCheck(error);
				
				double receive_rate =0;	     //剩余利息
				if(flag){//提前还款   返给理财人剩余利息
					receive_rate = receiveInterest - realReceiveInterest ;
				}
				
				//7. 修改投资账单的收款情况
		 		String updateSql = "update t_bill_invests set status = ?, real_receive_time = ?, real_receive_corpus = ? ,real_receive_interest = real_receive_interest + ? " +
		 				"  where user_id = ? and  bid_id = ?  and  periods = ?  and  status not in(?,?)  and invest_id = ?";
		 		
		        Query query = em.createQuery(updateSql).setParameter(1, Constants.NORMAL_RECEIVABLES).setParameter(2, DateUtil.currentDate())
		        .setParameter(3, receiveCorpus) .setParameter(4, receive_rate).setParameter(5, investUserId).
		        setParameter(6, this.bidId).setParameter(7, this.periods).setParameter(8, Constants.NORMAL_RECEIVABLES).setParameter(9, Constants.FOR_DEBT_MARK).setParameter(10, investId);
		        
		        try {
		        	rows = query.executeUpdate();
				} catch(Exception e) {
					e.printStackTrace();
					Logger.info("修改投资账单的收款情况时："+e.getMessage());
					error.code = -6;
					error.msg = "还款出现异常，导致还款失败";
					JPA.setRollbackOnly();
					
					return error.code;
				}
				
				if(rows == 0){
					error.code = -1;
					error.msg = "还款出现异常，导致还款失败";
					JPA.setRollbackOnly();
					
					return error.code;
				}
				
				//6.投资人余额增加获取的投资本金和利息
				if(invest_status != Constants.ONE){
					
					String balanceSql = "update t_users set balance = balance + ? where id = ?";
					//投资本金减投资管理费
					Query BalanceUpdate = em.createQuery(balanceSql).setParameter(1, realReceiveInterest).setParameter(2, investUserId);
					
					try {
						rows = BalanceUpdate.executeUpdate();
						
					} catch(Exception e) {
						e.printStackTrace();
						Logger.info("返回每个投资用户每期获得投资本金时："+e.getMessage());
						error.code = -5;
						error.msg = "数据库异常，导致还款失败";
						JPA.setRollbackOnly();
						
						return error.code;
					}
					
					if(rows == 0){
						error.code = -1;
						error.msg = "还款出现异常，导致还款失败";
						JPA.setRollbackOnly();
						
						return error.code;
					}
				
				
				
				Map<String, Double> investForDetail = DealDetail.queryUserFund(investUserId, error);
				if(error.code < 0 || investForDetail == null) {
					JPA.setRollbackOnly();
					
					return -1;
				}
				
				double investFreeze = investForDetail.get("freeze");
				double investReceiveAmount = investForDetail.get("receive_amount");
				
				Double investerBalance = this.queryBalance(investUserId, error);
				
				DealDetail investDetail = new DealDetail(investUserId, DealType.NOMAL_RECEIVE, receiveCorpus,
						investBillId, investerBalance, investFreeze, investReceiveAmount, "正常收款获取第"+this._id+"号账单投资金额");
				
				//添加正常收款的交易记录
				investDetail.addDealDetail(error);
				if(error.code < 0) {
					JPA.setRollbackOnly();
					
					return -1;
				}
				
				//6.投资人可用余额获得剩余利息
				if(receive_rate > 0){
					String balanceSql1 = "update t_users set balance = balance + ? where id = ? ";
					
					Query BalanceUpdate1 = em.createQuery(balanceSql1).setParameter(1, receive_rate).setParameter(2, investUserId);
					
					try {
						rows = BalanceUpdate1.executeUpdate();
						
					} catch(Exception e) {
						e.printStackTrace();
						Logger.info("扣取投资管理费时："+e.getMessage());
						error.code = -5;
						error.msg = "数据库异常，导致还款失败";
						JPA.setRollbackOnly();
						
						return error.code;
					}
					
					if(rows == 0){
						error.code = -1;
						error.msg = "还款出现异常，导致还款失败";
						JPA.setRollbackOnly();
						
						return error.code;
					}
					
					
					//本次查询用户可用余额是获取最新状态值，避免从缓存里拿数据
					Double investerBalance2 = this.queryBalance(investUserId, error);
					
					DealDetail investFeeDetail = new DealDetail(investUserId, DealType.NOMAL_RECEIVE, receive_rate,
							investBillId, investerBalance2, investFreeze, investReceiveAmount, "正常收款获取第"+this._id+"号账单剩余利息");
					
					//添加扣交易记录
					investFeeDetail.addDealDetail(error);
					if(error.code < 0) {
						JPA.setRollbackOnly();
						
						return -1;
					}
				}
				
				
				//6.投资人可用余额减去投资管理费
				String balanceSql2 = "update t_users set balance = balance - ? where id = ? and balance >= ?";
				
				Query  BalanceUpdate2 = em.createQuery(balanceSql2).setParameter(1, manageFee).setParameter(2, investUserId).setParameter(3, manageFee);
				
				try {
					rows = BalanceUpdate2.executeUpdate();
					
				} catch(Exception e) {
					e.printStackTrace();
					Logger.info("扣取投资管理费时："+e.getMessage());
					error.code = -5;
					error.msg = "数据库异常，导致还款失败";
					JPA.setRollbackOnly();
					
					return error.code;
				}
				
				if(rows == 0){
					error.code = -1;
					error.msg = "还款出现异常，导致还款失败";
					JPA.setRollbackOnly();
					
					return error.code;
				}
				
				
				//本次查询用户可用余额是获取最新状态值，避免从缓存里拿数据
				Double investerBalance3 = this.queryBalance(investUserId, error);
				
				DealDetail investFeeDetail2 = new DealDetail(investUserId, DealType.CHARGE_INVEST_FEE, manageFee,
						investBillId, investerBalance3, investFreeze, investReceiveAmount, "扣除第"+this._id+"号账单理财管理费");
				
				//添加扣除理财管理费的交易记录
				investFeeDetail2.addDealDetail(error);
				if(error.code < 0) {
					JPA.setRollbackOnly();
					
					return -1;
				}
				
				
				//最后把借款人的本金还回去并且扣除利息
				/*String  sql = "update t_users set balance = balance + ? where id = ?";
				Query  BalanceUpdate4 = em.createQuery(sql).setParameter(1, receiveCorpus-receiveInterest).setParameter(2, userId);
				
				try {
					rows = BalanceUpdate4.executeUpdate();
					
				} catch(Exception e) {
					e.printStackTrace();
					Logger.info("扣除借款人的利息"+e.getMessage());
					error.code = -5;
					error.msg = "数据库异常，导致还款失败";
					JPA.setRollbackOnly();
					
					return error.code;
				}
				
				if(rows == 0){
					error.code = -1;
					error.msg = "还款出现异常，导致还款失败";
					JPA.setRollbackOnly();
					
					return error.code;
				}
				*/
				Map<String, Object> userMap = new HashMap<String,Object>();
				
				String userSql = "select new Map(name as name, email as eamil, mobile as mobile) from t_users where id = ? ";
				
				try {
					userMap = t_users.find(userSql, investUserId).first();
				}catch(Exception e) {
					e.printStackTrace();
					Logger.info("根据用户id查询时："+e.getMessage());
					error.code = -1;
					error.msg = "查询用户名失败";
					
					return error.code;
				}
				
				String userName = (String)userMap.get("name");
				String userEamil = (String)userMap.get("eamil");
				String userMobile = (String)userMap.get("mobile");
				
				//发送站内信 尊敬的username:\n 您投资的借款title,第repayPeriod期还款已经完成.<br/>paymentModeStr本期应得总额：
				//￥recivedSum,其中本金部分为：hasP元,利息部分：hasI元,实得逾期罚息：hasLFI元<br/>扣除投资管理费：
				//￥mFee元<br/>实得总额：￥msFee元
				TemplateStation station = new TemplateStation();
				station.id = Templets.M_INVEST_RECEIVE;
				
				if(station.status) {
					String mContent = station.content.replace("userName",userName);
					mContent = mContent.replace("title",title);
					mContent = mContent.replace("repayPeriod",this.periods+"");
					mContent = mContent.replace("recivedSum",  DataUtil.formatString(receive));
					mContent = mContent.replace("hasP",  DataUtil.formatString(receiveCorpus));
					mContent = mContent.replace("hasI",  DataUtil.formatString(receiveInterest));
					mContent = mContent.replace("bchasY",  DataUtil.formatString(receive_rate));
					mContent = mContent.replace("hasLFI", "0.00");
					mContent = mContent.replace("mFee",  DataUtil.formatString(manageFee));
					mContent = mContent.replace("msFee",  DataUtil.formatString(Arith.round(receive - manageFee, 2)));
					mContent = mContent.concat("其中本金为体验金已收回");
					
					StationLetter letter = new StationLetter();
					letter.senderSupervisorId = 1;
					letter.receiverUserId = investUserId;
					letter.title = station.title;
					letter.content = mContent;
					 
					letter.sendToUserBySupervisor(error);
				}
				
				//发送邮件 尊敬的username:\n [date] 您在晓风安全网贷系统6对标的【title】还款了￥needSum 元
				TemplateEmail email = new TemplateEmail();
				email.id = Templets.E_INVEST_RECEIVE;
				
				if(email.status) {
					String eContent = email.content.replace("userName", userName);
					eContent = eContent.replace("title",title);
					eContent = eContent.replace("repayPeriod",this.periods+"");
					eContent = eContent.replace("recivedSum",  DataUtil.formatString(receive));                        
					eContent = eContent.replace("hasP",  DataUtil.formatString(receiveCorpus));                        
					eContent = eContent.replace("hasI",  DataUtil.formatString(receiveInterest));                      
					eContent = eContent.replace("hasLFI", "0.00");                                                     
					eContent = eContent.replace("mFee",  DataUtil.formatString(manageFee));                            
					eContent = eContent.replace("msFee",  DataUtil.formatString(Arith.round(receive - manageFee, 2))); 
					eContent = eContent.concat("其中本金为体验金已收回");
					email.addEmailTask(userEamil, email.title, eContent);
				}
				
				//尊敬的userName: 投资的编号bidId借款标repayPeriod期已成功还款，回款金额￥recivedSum元，扣除管理费￥mFee元，实得总额￥msFee元
				TemplateSms sms = new TemplateSms();
				sms.id = Templets.S_RECOVER_ADVANCE_SUCCESS;
				if(sms.status && StringUtils.isNotBlank(userMobile)) {
					String sContent = sms.content.replace("userName", userName);
					sContent = sContent.replace("title",title);
					sContent = sContent.replace("repayPeriod",this.periods+"");
					sContent = sContent.replace("recivedSum",  DataUtil.formatString(receive)); 
					sContent = sContent.replace("hasP",  DataUtil.formatString(receiveCorpus));
					sContent = sContent.replace("hasI",  DataUtil.formatString(receiveInterest));
					sContent = sContent.replace("bchasY",  DataUtil.formatString(receive_rate));
					sContent = sContent.replace("hasLFI", "0.00");
					sContent = sContent.replace("mFee",  DataUtil.formatString(manageFee));                            
					sContent = sContent.replace("msFee",  DataUtil.formatString(Arith.round(receive - manageFee, 2)));                                                   
					sContent = sContent.concat("其中本金为体验金已收回");
					TemplateSms.addSmsTask(userMobile, sContent);
				}
				
				//添加平台扣除理财管理费交易记录
				DealDetail.addPlatformDetail(DealType.INVEST_FEE, investBillId, investUserId, -1, DealType.ACCOUNT,
						manageFee, 1, "平台收取"+this._id+"投资管理费", error);
				if(error.code < 0) {
					JPA.setRollbackOnly();
					
					return -1;
				}
				
				/* 添加CPS推广费 */
				User.rewardCPS(investUserId, manageFee, investBillId, error);
				
				if (error.code < 0) {
					JPA.setRollbackOnly();

					return error.code;
				}
				
				//如果投资用户账户资金没有遭到非法改动，那么就更新其篡改标志，否则不更新
				if(investIsChange){
					investDataTamperproof.updateSignWithLock(investUserId, error);
				}
				
		     }else{
				
		 		DealDetail dealDetail = new DealDetail();
			
				/* 添加平台交易记录 */
				dealDetail.addPlatformDetail(DealType.ADD_PLATFORM_REGULAR,userId, userId, -1, 
						DealType.ACCOUNT, receiveCorpus, 1,
						"返还垫付给投资者赎回金额", error);
				
				if (error.code < 0) {
					JPA.setRollbackOnly();
					error.msg = "借款用户返还垫付给投资者赎回金额失败";
					return -1;
				}
				//用户事件添加
				DealDetail.userEvent(userId, UserEvent.ROLLBACK_SHUHUI, "返还赎回金额成功", error);
				
	    	}
    	 }
		}
		//手动付款模式
		else{
			String updateInvestSql = "update t_bill_invests set status = ? where bid_id = ? and periods = ? and status not in(?,?,?,?,?)";
			
	        Query updateInvest = em.createQuery(updateInvestSql).setParameter(1, Constants.FOR_PAY).setParameter(2, bidId)
	        .setParameter(3, period).setParameter(4, Constants.FOR_PAY).setParameter(5, Constants.NORMAL_RECEIVABLES)
	        .setParameter(6, Constants.ADVANCE_PRINCIIPAL_RECEIVABLES).setParameter(7, Constants.OVERDUE_RECEIVABLES).setParameter(8, Constants.FOR_DEBT_MARK);
			
			try {
				rows = updateInvest.executeUpdate();
			} catch(Exception e) {
				e.printStackTrace();
				Logger.info("修改投资账单状态时："+e.getMessage());
				error.code = -3;
				error.msg = "数据库异常，导致修改投资账单状态失败";
				JPA.setRollbackOnly();
				
				return error.code;
			}
		}
		
		//10.修改借款账单还款情况
		String updateBillSql = "update t_bills set status = ?, real_repayment_time = ?, real_repayment_corpus = ?, real_repayment_interest = ? where id = ? and status = ? ";
			
		Query updateBill = em.createQuery(updateBillSql).setParameter(1, Constants.NORMAL_REPAYMENT).setParameter(2, DateUtil.currentDate())
        .setParameter(3, repaymentCorpus).setParameter(4, repaymentInterest).setParameter(5, this._id).setParameter(6, Constants.NO_REPAYMENT);
		
		try {
			rows = updateBill.executeUpdate();
		} catch(Exception e) {
			e.printStackTrace();
			Logger.info("修改借款账单数据时："+e.getMessage());
			error.code = -3;
			error.msg = "还款出现异常，导致还款失败";
			JPA.setRollbackOnly();
			
			return error.code;
		}finally{
			double Userbalance = queryBalance(userId, error);//用户可用余额
			
			if(rows == 0){
				error.code = -1;
				error.msg = "还款出现异常，导致还款失败";
				JPA.setRollbackOnly();
				
				return error.code;
			}
			
			if(Userbalance < 0){
				error.code = -1;
				error.msg = "资金不足,请充值!";
				JPA.setRollbackOnly();
				
				return error.code;
			}
		}
		
		//扣除借款人还款本期的所需金额
		/*String userBalanceSql = "update t_users set balance = balance - ? where id = ? ";
		
		Query userBalance = em.createQuery(userBalanceSql).setParameter(1, 0.00).setParameter(2, userId);
		
		try {
			rows = userBalance.executeUpdate();
		} catch(Exception e) {
			e.printStackTrace();
			Logger.info("修改借款人账户金额时："+e.getMessage());
			error.code = -2;
			error.msg = "还款出现异常，导致还款失败";
			JPA.setRollbackOnly();
			
			return error.code;
		}
		
		if(rows == 0){
			error.code = -1;
			error.msg = "还款出现异常，导致还款失败";
			JPA.setRollbackOnly();
			
			return error.code;
		}*/
		
		//9.针对借款账单做交易记录和事件
		Map<String, Double> billForDetail2 = DealDetail.queryUserFund(userId, error);
		if(error.code < 0 || billForDetail2 == null) {
			JPA.setRollbackOnly();
			
			return -1;
		}
		
		double userFreeze3 = billForDetail2.get("freeze");
		double userReceiveAmount3 = billForDetail2.get("receive_amount");
			
		Double investerBalance10 = this.queryBalance(userId, error);
		
		DealDetail detail = new DealDetail(userId, DealType.CHARGE_OVERDUE_FEE, payment,
				this._id, investerBalance10, userFreeze3, userReceiveAmount3, "第"+this._id+"号账单正常还款扣除还款金额");
		
		//添加正常还款的交易记录
		detail.addDealDetail(error);
		if(error.code < 0) {
			JPA.setRollbackOnly();
			
			return -1;
		}
		
		//正常还款获得信用积分
		int creditScore = detail.addCreditScore(userId, 2, 0, this._id, "第"+this._id+"号账单正常还款获得信用积分", error);
		if(error.code < 0) {
			JPA.setRollbackOnly();
			
			return -1;
		}
		
		//更新用户的信用等级
		/* 
		 * v.7.2.7 不及时更新用户信用等级，每天凌晨统一更新
		User.updateCreditLevel(userId, error);
		if (error.code < 0) {
			JPA.setRollbackOnly();
			
			return -1;
		}
		*/
		
		//添加事件
		DealDetail.userEvent(userId, UserEvent.ADD_NOMAL_PAY, "第"+this._id+"号账单用户正常还款", error);
		if(error.code < 0) {
			JPA.setRollbackOnly();
			
			return -1;
		}
		
		Map<String, Object> userMap = new HashMap<String,Object>();
		
		String userSql = "select new Map(name as name, email as eamil, mobile as mobile) from t_users where id = ? ";
		
		try {
			userMap = t_users.find(userSql, userId).first();
		}catch(Exception e) {
			e.printStackTrace();
			Logger.info("根据用户id查询时："+e.getMessage());
			error.code = -1;
			error.msg = "查询用户名失败";
			JPA.setRollbackOnly();
			
			return error.code;
		}
		
		String userName = (String)userMap.get("name");
		String userEamil = (String)userMap.get("eamil");
		String userMobile = (String)userMap.get("mobile");
		
		//发送站内信  尊敬的username:\n [date] 您在晓风安全网贷系统6对标的【title】还款了￥needSum 元
		TemplateStation station = new TemplateStation();
		station.id = Templets.M_SUCCESS_PAY;
		
		if(station.status) {
			String mContent = station.content.replace("userName", userName);
			mContent = mContent.replace("date", DateUtil.dateToString(new Date()));
			mContent = mContent.replace("bidId",backstageSet.loanNumber+bidId);
			mContent = mContent.replace("repayPeriod",this.periods+"");
			mContent = mContent.replace("needSum",  DataUtil.formatString(payment+repaymentInterest));
			mContent = mContent.replace("rcp",  DataUtil.formatString(repaymentCorpus));
			mContent = mContent.replace("hasI",  DataUtil.formatString(repaymentInterest));
			mContent = mContent.replace("rodf",  "0.00");
			mContent = mContent.replace("creditScore",creditScore+"");
			
			StationLetter letter = new StationLetter();
			letter.senderSupervisorId = 1;
			letter.receiverUserId = userId;
			letter.title = station.title;
			letter.content = mContent;
			 
			letter.sendToUserBySupervisor(error);
		}
		
		//发送邮件 尊敬的username:\n [date] 您在晓风安全网贷系统6对标的【title】还款了￥needSum 元
		TemplateEmail email = new TemplateEmail();
		email.id = Templets.E_SUCCESS_PAY;
		
		if(email.status) {
			String eContent = email.content.replace("userName", userName);
			eContent = eContent.replace("date", DateUtil.dateToString(new Date()));
			eContent = eContent.replace("title",title);
			eContent = eContent.replace("needSum",payment+"");
			eContent = eContent.replace("creditScore",creditScore+"");
			email.addEmailTask(userEamil, email.title, eContent);
		}
		
		//发送短信  尊敬的userName: 你申请的编号bidId借款标repayPeriod期成功还款，还款金额￥needSum元，
		TemplateSms sms = new TemplateSms();
		sms.id = Templets.S_REPAY_SUCCESS;
		if(sms.status && StringUtils.isNotBlank(userMobile)) {
			String eContent = sms.content.replace("userName", userName);
			eContent = eContent.replace("bidId",backstageSet.loanNumber+bidId);
			eContent = eContent.replace("repayPeriod",this.periods+"");
			eContent = eContent.replace("needSum",DataUtil.formatString(payment+repaymentInterest));
			eContent = eContent.replace("rcp",  DataUtil.formatString(repaymentCorpus));
			eContent = eContent.replace("hasI",  DataUtil.formatString(repaymentInterest));
			eContent = eContent.replace("rodf",  "0.00");
			TemplateSms.addSmsTask(userMobile, eContent);
		}
		
		//11.判断这个借款标是否已还完款，若还完标记这个借款标为已还款完状态
		if(this.isEndPayment(bidId, error) == 0){
			String bidSql = "update t_bids set status = ?, last_repay_time = ? where id = ?";
		
            Query query = em.createQuery(bidSql).setParameter(1, Constants.BID_REPAYMENTS).setParameter(2, DateUtil.currentDate())
            .setParameter(3, bidId);
            
			try {
				rows = query.executeUpdate();;
			
			} catch(Exception e) {
				e.printStackTrace();
				Logger.info("修改借款标为已还款完状态时："+e.getMessage());
				error.code = -7;
				error.msg = "还款出现异常，导致还款失败";
				JPA.setRollbackOnly();
				
				return error.code;
			}
			
			if(rows == 0){
				error.code = -1;
				error.msg = "还款出现异常，导致还款失败";
				JPA.setRollbackOnly();
			
				return error.code;
			}
		}
		
		//12.更改当前用户在缓存变动的数据
		/*User user = User.currUser();
		
		if(user != null){
			user.balance = lastBalance;
			user.creditScore = user.creditScore + creditScore;
			user.setCurrUser(user);
		}*/
		
		//13.还款完毕后重新生成新的数据库防篡改标志
		DataSafety dataTamperproof = new DataSafety();
		dataTamperproof.updateSignWithLock(userId, error);
		
		error.msg = "还款成功";
		error.code = 0;
		return error.code;
 	}
 	
 	/**
 	 * 体验金逾期还款
 	 */
 	public int overduePayment2(long bidId, long userId, double repaymentCorpus, double repaymentInterest, double balance, 
 			List<Map<String, Object>> investList, int status, double repayOverdueFine, int period, ErrorInfo error) {
 		error.clear();
 		
 		double managementRate = Bid.queryInvestRate(bidId);
 		
 		if(managementRate != 0){
 			managementRate = managementRate / 100;
		}
 		
 		double payment = repaymentCorpus  + repayOverdueFine;// 总共要还款的金额
		EntityManager em = JPA.em();
		int rows = 0;
		
		String bidTitleSql = "select title from t_bids where id = ? ";
		String title = null;
		
		try {
			title = t_bids.find(bidTitleSql, bidId).first();
		}catch(Exception e) {
			e.printStackTrace();
			Logger.info("根据标id查询时："+e.getMessage());
			error.code = -1;
			error.msg = "查询用户名失败";
			
			return error.code;
		}
		
		//5.非本金垫付还款的操作
		if (status == Constants.NO_REPAYMENT){
			
			BackstageSet backstageSet = BackstageSet.getCurrentBackstageSet();
			
			//判断系统应付账单设置（0为自动付款，1为手动付款）
			if(backstageSet.repayType.equalsIgnoreCase(Constants.AUTO_PAYMENT)){

			//a.遍历本期的投资账单，计算并操作对应的还款金额到投资人账上

			for (Map<String, Object> map : investList) {
				JSONObject json = JSONObject.fromObject(map);
				long investId = json.getLong("investId");//投资id
				long investBillId = json.getLong("id");//每个投资人的投资账单id
				long investUserId = json.getLong("user_id");//投资人id
				double receiveCorpus = json.getDouble("receive_corpus");// 本期的投资本金
				double receiveInterest = json.getDouble("receive_interest");// 本期的投资利息
				double investOverdueFine = json.getDouble("overdue_fine");// 本期逾期罚款
				int    invest_status     = json.getInt("invest_status");// 投资账单状态
				double manageFee = BillInvests.getInvestManagerFee(receiveInterest, managementRate, investUserId);  //投资管理费

				double receive = Arith.round(receiveCorpus+ receiveInterest  + investOverdueFine, 2);//计算投资人总共将获取的金额
	
				//查看投资用户是否受到非法资金改动
				DataSafety investDataTamperproof = new DataSafety();
				investDataTamperproof.id = investUserId;
				boolean investIsChange = investDataTamperproof.signCheck(error);
				
				// c.填写投资账单的收款情况
				String updateSql = "update t_bill_invests set status = ?, real_receive_time = ?, real_receive_corpus = ?," +
						"  overdue_fine = ? where user_id = ? and bid_id = ? and periods = ? and status not in(?,?) and invest_id = ?";
				
		        Query query = em.createQuery(updateSql).setParameter(1, Constants.OVERDUE_RECEIVABLES).setParameter(2, DateUtil.currentDate())
		        .setParameter(3, receiveCorpus).setParameter(4, investOverdueFine)
		        .setParameter(5, investUserId).setParameter(6, this.bidId).setParameter(7, this.periods)
		        .setParameter(8, Constants.OVERDUE_RECEIVABLES).setParameter(9, Constants.FOR_DEBT_MARK).setParameter(10, investId);
		        
		        try {
					rows = query.executeUpdate();
				} catch(Exception e) {
					e.printStackTrace();
					Logger.info("填写投资账单的收款情况时："+e.getMessage());
					error.code = -5;
					error.msg = "还款出现异常，导致还款失败";
					JPA.setRollbackOnly();
					
					return error.code;
				}
				
				if(rows == 0){
					error.code = -1;
					error.msg = "还款出现异常，导致还款失败";
					JPA.setRollbackOnly();
					
					return error.code;
					
				}
				
				// b.重新计算每个投资用户获取本期还款(本金+利息)金额
				if(invest_status != Constants.ONE){
				String balanceSql = "update t_users set balance = balance + ? where id =?";
				
				Query BalanceUpdate = em.createQuery(balanceSql).setParameter(1, receiveInterest).setParameter(2, investUserId);
				
				try {
					rows = BalanceUpdate.executeUpdate();
					
				} catch(Exception e) {
					e.printStackTrace();
					Logger.info("返回每个投资用户每期获得的投资本金以及罚息时："+e.getMessage());
					error.code = -5;
					error.msg = "还款出现异常，导致还款失败";
					JPA.setRollbackOnly();
					
					return error.code;
				}
				
				if(rows == 0){
					error.code = -1;
					error.msg = "还款出现异常，导致还款失败";
					JPA.setRollbackOnly();
					
					return error.code;
				}
				
				//d.开始添加投资人交易记录和事件
				Map<String, Double> investForDetail = DealDetail.queryUserFund(investUserId, error);
				if(error.code < 0 || investForDetail == null) {
					JPA.setRollbackOnly();
					
					return -1;
				}
				
				double investFreeze = investForDetail.get("freeze");
				double investReceiveAmount = investForDetail.get("receive_amount");
				
				Double investBalance10 = this.queryBalance(investUserId, error);
				
				DealDetail investDetail = new DealDetail(investUserId, DealType.OVER_RECEIVE, receiveCorpus,
						investBillId, investBalance10, investFreeze, investReceiveAmount, "逾期收款获取第"+this._id+"号账单投资金额");
				
				//添加交易记录
				investDetail.addDealDetail(error);
				if(error.code < 0) {
					JPA.setRollbackOnly();
					
					return -1;
				}
				
				// d.投资人添加逾期费
				String balanceSql3 = "update t_users set balance = balance + ? where id =?";
				
				Query BalanceUpdate3 = em.createQuery(balanceSql3).setParameter(1, investOverdueFine).setParameter(2, investUserId);
				
				try {
					rows = BalanceUpdate3.executeUpdate();
					
				} catch(Exception e) {
					e.printStackTrace();
					Logger.info("投资人添加逾期费："+e.getMessage());
					error.code = -5;
					error.msg = "还款出现异常，导致还款失败";
					JPA.setRollbackOnly();
					
					return error.code;
				}
				
				if(rows == 0){
					error.code = -1;
					error.msg = "还款出现异常，导致还款失败";
					JPA.setRollbackOnly();
					
					return error.code;
				}
				
				Double investBalance3 = this.queryBalance(investUserId, error);
			
				DealDetail investOverdueFeeDetail = new DealDetail(investUserId, DealType.ADD_OVERDUE_FEE, investOverdueFine,
						investBillId, investBalance3, investFreeze, investReceiveAmount, "获取第"+this._id+"号账单逾期费");
				
				//添加获取逾期费用的交易记录
				investOverdueFeeDetail.addDealDetail(error);
				if(error.code < 0) {
					JPA.setRollbackOnly();
					
					return -1;
				}
				
				// c.投资人减去管理费
				String balanceSql2 = "update t_users set balance = balance - ? where id =? and balance >= ?";
				
				Query BalanceUpdate2 = em.createQuery(balanceSql2).setParameter(1, manageFee).setParameter(2, investUserId).setParameter(3, manageFee);
				
				try {
					rows = BalanceUpdate2.executeUpdate();
					
				} catch(Exception e) {
					e.printStackTrace();
					Logger.info("投资人减去管理费："+e.getMessage());
					error.code = -5;
					error.msg = "还款出现异常，导致还款失败";
					JPA.setRollbackOnly();
					
					return error.code;
				}
				
				if(rows == 0){
					error.code = -1;
					error.msg = "还款出现异常，导致还款失败";
					JPA.setRollbackOnly();
					
					return error.code;
				}
				
				Double investBalance2 = this.queryBalance(investUserId, error);
				
				DealDetail investFeeDetail = new DealDetail(investUserId, DealType.CHARGE_INVEST_FEE, manageFee,
						investBillId, investBalance2, investFreeze, investReceiveAmount, "扣除第"+this._id+"号账单理财管理费");
				
				//添加扣除理财管理费的交易记录
				investFeeDetail.addDealDetail(error);
				if(error.code < 0) {
					JPA.setRollbackOnly();
					
					return -1;
				}
				
				
				
				
				//添加平台扣除理财管理费交易记录
				DealDetail.addPlatformDetail(DealType.INVEST_FEE, investBillId, investUserId, -1, DealType.ACCOUNT, manageFee, 1, "平台收取第"+this._id+"号账单投资管理费", error);
				if(error.code < 0) {
					JPA.setRollbackOnly();
					
					return -1;
				}
				
				/* 添加CPS推广费 */
				User.rewardCPS(investUserId, manageFee, investBillId, error);
				
				if (error.code < 0) {
					JPA.setRollbackOnly();

					return error.code;
				}
				
                Map<String, Object> userMap = new HashMap<String,Object>();
				
				String userSql = "select new Map(name as name, email as eamil, mobile as mobile) from t_users where id = ? ";
				
				try {
					userMap = t_users.find(userSql, investUserId).first();
				}catch(Exception e) {
					e.printStackTrace();
					Logger.info("根据用户id查询时："+e.getMessage());
					error.code = -1;
					error.msg = "查询用户名失败";
					JPA.setRollbackOnly();
					
					return error.code;
				}
				
				String userName = (String)userMap.get("name");
				String userEamil = (String)userMap.get("eamil");
				String userMobile = (String)userMap.get("mobile");
				
				//发送站内信 尊敬的username:\n 您投资的借款title,第repayPeriod期还款已经完成.<br/>paymentModeStr本期应得总额：
				//￥recivedSum,其中本金部分为：hasP元,利息部分：hasI元,实得逾期罚息：hasLFI元<br/>扣除投资管理费：
				//￥mFee元<br/>实得总额：￥msFee元
				TemplateStation station = new TemplateStation();
				station.id = Templets.M_INVEST_RECEIVE;
				
				if(station.status) {
					String mContent = station.content.replace("userName",userName);
					mContent = mContent.replace("title",title);
					mContent = mContent.replace("repayPeriod",this.periods+"");
					mContent = mContent.replace("recivedSum",  DataUtil.formatString(receive));
					mContent = mContent.replace("hasP",  DataUtil.formatString(receiveCorpus));
					mContent = mContent.replace("hasI",  DataUtil.formatString(receiveInterest));
					mContent = mContent.replace("bchasY",  "0.00");
					mContent = mContent.replace("hasLFI",  DataUtil.formatString(investOverdueFine));
					mContent = mContent.replace("mFee",  DataUtil.formatString(manageFee));
					mContent = mContent.replace("msFee",  DataUtil.formatString(Arith.round(receive - manageFee, 2)));
					mContent = mContent.concat("其中体验金已收回!");
					StationLetter letter = new StationLetter();
					letter.senderSupervisorId = 1;
					letter.receiverUserId = investUserId;
					letter.title = station.title;
					letter.content = mContent;
					 
					letter.sendToUserBySupervisor(error);
				}
				
				//发送邮件 尊敬的username:\n [date] 您在晓风安全网贷系统6对标的【title】还款了￥needSum 元
				TemplateEmail email = new TemplateEmail();
				email.id = Templets.E_INVEST_RECEIVE;
				
				if(email.status) {
					String eContent = email.content.replace("userName", userName);
					eContent = eContent.replace("title",title);
					eContent = eContent.replace("repayPeriod",this.periods+"");
					eContent = eContent.replace("recivedSum",  DataUtil.formatString(receive));                       
					eContent = eContent.replace("hasP",  DataUtil.formatString(receiveCorpus));                       
					eContent = eContent.replace("hasI",  DataUtil.formatString(receiveInterest));                     
					eContent = eContent.replace("hasLFI",  DataUtil.formatString(investOverdueFine));                 
					eContent = eContent.replace("mFee",  DataUtil.formatString(manageFee));                           
					eContent = eContent.replace("msFee",  DataUtil.formatString(Arith.round(receive - manageFee, 2)));
					eContent = eContent.concat("其中体验金已收回!");
					email.addEmailTask(userEamil, email.title, eContent);
				}
				
				//尊敬的userName: 投资的编号bidId借款标repayPeriod期已成功还款，回款金额￥recivedSum元，扣除管理费￥mFee元，实得总额￥msFee元
				TemplateSms sms = new TemplateSms();
				sms.id = Templets.S_RECOVER_ADVANCE_SUCCESS;
				if(sms.status && StringUtils.isNotBlank(userMobile)) {
					String sContent = sms.content.replace("userName", userName);
					sContent = sContent.replace("title",title);
					sContent = sContent.replace("repayPeriod",this.periods+"");
					sContent = sContent.replace("recivedSum",  DataUtil.formatString(receive));
					sContent = sContent.replace("hasP",  DataUtil.formatString(receiveCorpus));
					sContent = sContent.replace("hasI",  DataUtil.formatString(receiveInterest));
					sContent = sContent.replace("bchasY",  "0.00");
					sContent = sContent.replace("hasLFI",  DataUtil.formatString(investOverdueFine));
					sContent = sContent.replace("mFee",  DataUtil.formatString(manageFee));
					sContent = sContent.replace("msFee",  DataUtil.formatString(Arith.round(receive - manageFee, 2)));                                                  
					sContent = sContent.concat("其中体验金已收回!");
					TemplateSms.addSmsTask(userMobile, sContent);
				}
				
				//如果投资用户账户资金没有遭到非法改动，那么就更新其篡改标志，否则不更新
				if(investIsChange){
					investDataTamperproof.updateSignWithLock(investUserId, error);
				} 
			   }else{
					
			 		DealDetail dealDetail = new DealDetail();
				
					/* 添加平台交易记录 */
					dealDetail.addPlatformDetail(DealType.ADD_PLATFORM_REGULAR,userId, userId, -1, 
							DealType.ACCOUNT, receiveCorpus, 1,
							"返还垫付给投资者赎回金额", error);
					
					if (error.code < 0) {
						JPA.setRollbackOnly();
						error.msg = "借款用户返还垫付给投资者赎回金额失败";
						return -1;
					}
					//用户事件添加
					DealDetail.userEvent(userId, UserEvent.ROLLBACK_SHUHUI, "返还赎回金额成功", error);
					
		    	}
			 }
			}
			//手动付款模式
			else{
				String updateInvestSql = "update t_bill_invests set status = ? where bid_id = ? and periods = ? and status not in(?,?,?,?,?,?)";
				
		        Query updateInvest = em.createQuery(updateInvestSql).setParameter(1, Constants.FOR_OVERDUE_PAY).setParameter(2, bidId)
		        .setParameter(3, period).setParameter(4, Constants.FOR_PAY).setParameter(5, Constants.FOR_OVERDUE_PAY).setParameter(6, Constants.NORMAL_RECEIVABLES)
		        .setParameter(7, Constants.ADVANCE_PRINCIIPAL_RECEIVABLES).setParameter(8, Constants.OVERDUE_RECEIVABLES).setParameter(9, Constants.FOR_DEBT_MARK);
				
				try {
					rows = updateInvest.executeUpdate();
				} catch(Exception e) {
					e.printStackTrace();
					Logger.info("修改投资账单状态时："+e.getMessage());
					error.code = -3;
					error.msg = "数据库异常，导致修改投资账单状态失败";
					JPA.setRollbackOnly();
					
					return error.code;
				}
			}
			
		}
		else{
			//6.如果为本金垫付，扣完借款人还款金额后直接添加平台收取本金垫付交易记录
			DealDetail.addPlatformDetail(DealType.ADVANCE_FEE, this._id, userId, -1, DealType.ACCOUNT, payment, 1, "平台收取第"+this._id+"号账单垫付金额", error);
			if(error.code < 0) {
				JPA.setRollbackOnly();
				
				return -1;
			}
		}
		
		//7.修改借款账单还款情况
		String updateBillSql = "update t_bills set status = ?, real_repayment_time = ?, real_repayment_corpus = ?, real_repayment_interest = ?, overdue_fine = ? where id = ? and status in (?,?)";
			
		Query updateBill = em.createQuery(updateBillSql).setParameter(1, Constants.OVERDUE_PATMENT).setParameter(2, DateUtil.currentDate())
        .setParameter(3, repaymentCorpus).setParameter(4, repaymentInterest).setParameter(5, repayOverdueFine)
        .setParameter(6, this._id).setParameter(7, Constants.NO_REPAYMENT).setParameter(8, Constants.ADVANCE_PRINCIIPAL_REPAYMENT);
		
		try {
			rows = updateBill.executeUpdate();
		} catch(Exception e) {
			e.printStackTrace();
			Logger.info("修改借款账单数据时："+e.getMessage());
			error.code = -3;
			error.msg = "还款出现异常，导致还款失败";
			JPA.setRollbackOnly();
			
			return error.code;
		}finally{
			double Userbalance = queryBalance(userId, error);//用户可用余额
			
			if(rows == 0){
				error.code = -1;
				error.msg = "还款出现异常，导致还款失败";
				JPA.setRollbackOnly();
				
				return error.code;
			}
			
			if(Userbalance < 0){
				error.code = -1;
				error.msg = "资金不足,请充值!";
				JPA.setRollbackOnly();
				
				return error.code;
			}
		}
		
		//8.判断这个借款标是否已还完款，如果还完后给这个借款标标记为已还完状态
		if(this.isEndPayment(bidId, error) == 0){
			String bidSql = "update t_bids set status = ?, last_repay_time = ? where id = ?";
		
            Query query = em.createQuery(bidSql).setParameter(1, Constants.BID_REPAYMENTS).setParameter(2, DateUtil.currentDate())
            .setParameter(3, bidId);
            
			try {
				rows = query.executeUpdate();;
			
			} catch(Exception e) {
				e.printStackTrace();
				Logger.info("修改借款标为已还款完状态时："+e.getMessage());
				error.code = -7;
				error.msg = "还款出现异常，导致还款失败";
				JPA.setRollbackOnly();
				
				return error.code;
			}
			
			if(rows == 0){
				error.code = -1;
				error.msg = "还款出现异常，导致还款失败";
				JPA.setRollbackOnly();
			
				return error.code;
			}
		}
		
		//4.扣除借款人还款本期的本金和逾期费
		String userBalanceSql = "update t_users set balance = balance - ? where id = ? ";
		
		Query userBalanceUpdate = em.createQuery(userBalanceSql).setParameter(1,0.00).setParameter(2, userId);
		
		try {
			rows = userBalanceUpdate.executeUpdate();
		} catch(Exception e) {
			e.printStackTrace();
			Logger.info("修改借款人账户金额时："+e.getMessage());
			error.code = -2;
			error.msg = "还款出现异常，导致还款失败";
			JPA.setRollbackOnly();
			
			return error.code;
		}
		
		if(rows == 0){
			error.code = -1;
			error.msg = "还款出现异常，导致还款失败";
			JPA.setRollbackOnly();
			
			return error.code;
		}
		
		//9.添加有关借款账单还款的交易记录和事件
		Map<String, Double> billForDetail = DealDetail.queryUserFund(userId, error);
		if(error.code < 0 || billForDetail == null) {
			JPA.setRollbackOnly();
			
			return -1;
		}
		
		double userFreeze = billForDetail.get("freeze");
		double userReceiveAmount = billForDetail.get("receive_amount");
		
		Double investBalance15 = this.queryBalance(userId, error);
		
		DealDetail detail = new DealDetail(userId, DealType.CHARGE_OVER_PAY, repaymentCorpus,
				this._id, investBalance15, userFreeze, userReceiveAmount, "第"+this._id+"号账单逾期还款扣除还款金额");
		
		//添加逾期还款的交易记录
		detail.addDealDetail(error);
		if(error.code < 0) {
			JPA.setRollbackOnly();
			
			return -1;
		}
		
		//4.扣除借款人还款本期的逾期费用
		String userBalanceSql2 = "update t_users set balance = balance - ? where id = ?";
		
		Query userBalanceUpdate2 = em.createQuery(userBalanceSql2).setParameter(1, 0.00).setParameter(2, userId);
		
		try {
			rows = userBalanceUpdate2.executeUpdate();
		} catch(Exception e) {
			e.printStackTrace();
			Logger.info("修改借款人账户金额时："+e.getMessage());
			error.code = -2;
			error.msg = "还款出现异常，导致还款失败";
			JPA.setRollbackOnly();
			
			return error.code;
		}
		
		if(rows == 0){
			error.code = -1;
			error.msg = "还款出现异常，导致还款失败";
			JPA.setRollbackOnly();
			
			return error.code;
		}
		
		//本次查询用户可用余额是获取最新状态值，避免从缓存里拿数据
		Double userBalance2 = this.queryBalance(userId, error);
		
		DealDetail overdueDetail = new DealDetail(userId, DealType.CHARGE_OVERDUE_FEE, repayOverdueFine,
				this._id, userBalance2, userFreeze, userReceiveAmount, "第"+this._id+"号账单逾期还款扣除逾期费");
		
		//添加逾期还款扣除逾期费的交易记录
		overdueDetail.addDealDetail(error);
		if(error.code < 0) {
			JPA.setRollbackOnly();
			
			return -1;
		}
		
		//逾期还款减去信用积分
		int creditScore = detail.addCreditScore(userId, -1, 0, this._id, "第"+this._id+"号账单逾期还款扣除信用积分", error);
		if(error.code < 0) {
			JPA.setRollbackOnly();
			
			return -1;
		}
		
		//更新用户信用等级
		/* 
		 * v.7.2.7不及时更新用户信用等级，每天凌晨统一更新
		User.updateCreditLevel(userId, error);
		if (error.code < 0) {
			JPA.setRollbackOnly();
			
			return error.code;
		}
		*/
		
		//添加事件
		DealDetail.userEvent(userId, UserEvent.ADD_NOMAL_PAY, "第"+this._id+"号账单用户逾期还款", error);
		if(error.code < 0) {
			JPA.setRollbackOnly();
			
			return -1;
		}
		
        Map<String, Object> userMap = new HashMap<String,Object>();
		
		String userSql = "select new Map(name as name, email as eamil, mobile as mobile) from t_users where id = ? ";
		
		try {
			userMap = t_users.find(userSql, userId).first();
		}catch(Exception e) {
			e.printStackTrace();
			Logger.info("根据用户id查询时："+e.getMessage());
			error.code = -1;
			error.msg = "查询用户名失败";
			
			return error.code;
		}
		
		String userName = (String)userMap.get("name");
		String userEamil = (String)userMap.get("eamil");
		String userMobile = (String)userMap.get("mobile");
		
		//发送站内信  尊敬的username:\n [date] 您在晓风安全网贷系统6对标的【title】还款了￥needSum 元
		TemplateStation station = new TemplateStation();
		station.id = Templets.M_SUCCESS_PAY;
		BackstageSet backstageSet = BackstageSet.getCurrentBackstageSet();
		
		if(station.status) {
			String mContent = station.content.replace("userName", userName);
			mContent = mContent.replace("date",DateUtil.dateToString(new Date()));
			mContent = mContent.replace("bidId",backstageSet.loanNumber+bidId);
			mContent = mContent.replace("repayPeriod",this.periods+"");
			mContent = mContent.replace("needSum",  DataUtil.formatString(payment));
			mContent = mContent.replace("rcp",DataUtil.formatString(repaymentCorpus));
			mContent = mContent.replace("hasI",DataUtil.formatString(repaymentInterest));
			mContent = mContent.replace("rodf",DataUtil.formatString(repayOverdueFine));
			mContent = mContent.replace("creditScore",-creditScore+"");
			
			StationLetter letter = new StationLetter();
			letter.senderSupervisorId = 1;
			letter.receiverUserId = userId;
			letter.title = station.title;
			letter.content = mContent;
			 
			letter.sendToUserBySupervisor(error);
		}
		
		//发送邮件 尊敬的username:\n [date] 您在晓风安全网贷系统6对标的【title】还款了￥needSum 元
		TemplateEmail email = new TemplateEmail();
		email.id = Templets.E_SUCCESS_PAY;

		if(email.status) {
			String eContent = email.content.replace("userName", userName);
			eContent = eContent.replace("date",DateUtil.dateToString(new Date()));
			eContent = eContent.replace("title",title);
			eContent = eContent.replace("needSum",  DataUtil.formatString(payment));
			eContent = eContent.replace("creditScore",-creditScore+"");
			email.addEmailTask(userEamil, email.title, eContent);
		}
		
		//发送短信  尊敬的userName: 你申请的编号bidId借款标repayPeriod期成功还款，还款金额￥needSum元，
		TemplateSms sms = new TemplateSms();
	
		sms.id = Templets.S_REPAY_SUCCESS;
		if(sms.status && StringUtils.isNotBlank(userMobile)) {
			String eContent = sms.content.replace("userName", userName);
			eContent = eContent.replace("bidId",backstageSet.loanNumber+bidId);
			eContent = eContent.replace("repayPeriod",this.periods+"");
			eContent = eContent.replace("needSum",DataUtil.formatString(payment));
			eContent = eContent.replace("rcp",DataUtil.formatString(repaymentCorpus));
			eContent = eContent.replace("hasI",DataUtil.formatString(repaymentInterest));
			eContent = eContent.replace("rodf",DataUtil.formatString(repayOverdueFine));

			TemplateSms.addSmsTask(userMobile, eContent);
		}
		
		//10.更改当前用户在缓存变动的数据
		/*User user = User.currUser();
		user.balance = lastBalance;
		user.creditScore = user.creditScore + creditScore;
		user.setCurrUser(user);*/
		
		//11.还款完毕后重新生成新的数据库防篡改标志
		DataSafety dataTamperproof = new DataSafety();
		dataTamperproof.updateSignWithLock(userId, error);
		
		error.code = 0;
		error.msg = "还款成功";
		return error.code;
 	}
 	/**
 	 * 逾期还款
 	 * @param userId
 	 * @param repaymentCorpus
 	 * @param repaymentInterest
 	 * @param balance
 	 * @param managementRate
 	 * @param list
 	 * @param info
 	 * @return
 	 * @throws ParseException 
 	 */
 	public int overduePayment(long bidId, long userId, double repaymentCorpus, double repaymentInterest, double balance, 
 			List<Map<String, Object>> investList, int status, double repayOverdueFine, int period, ErrorInfo error) {
 		error.clear();
 		
 		double managementRate = Bid.queryInvestRate(bidId);
 		
 		if(managementRate != 0){
 			managementRate = managementRate / 100;
		}
 		
 		double payment = repaymentCorpus  + repayOverdueFine;// 总共要还款的金额
		EntityManager em = JPA.em();
		int rows = 0;
		
		String bidTitleSql = "select title from t_bids where id = ? ";
		String title = null;
		
		try {
			title = t_bids.find(bidTitleSql, bidId).first();
		}catch(Exception e) {
			e.printStackTrace();
			Logger.info("根据标id查询时："+e.getMessage());
			error.code = -1;
			error.msg = "查询用户名失败";
			
			return error.code;
		}
		
		//5.非本金垫付还款的操作
		if (status == Constants.NO_REPAYMENT){
			
			BackstageSet backstageSet = BackstageSet.getCurrentBackstageSet();
			
			//判断系统应付账单设置（0为自动付款，1为手动付款）
			if(backstageSet.repayType.equalsIgnoreCase(Constants.AUTO_PAYMENT)){

			//a.遍历本期的投资账单，计算并操作对应的还款金额到投资人账上

			for (Map<String, Object> map : investList) {
				JSONObject json = JSONObject.fromObject(map);
				long investId = json.getLong("investId");//投资id
				long investBillId = json.getLong("id");//每个投资人的投资账单id
				long investUserId = json.getLong("user_id");//投资人id
				double receiveCorpus = json.getDouble("receive_corpus");// 本期的投资本金
				double receiveInterest = json.getDouble("receive_interest");// 本期的投资利息
				double investOverdueFine = json.getDouble("overdue_fine");// 本期逾期罚款
				int    invest_status     = json.getInt("invest_status");// 投资账单状态
				double manageFee = BillInvests.getInvestManagerFee(receiveInterest, managementRate, investUserId);  //投资管理费

				double receive = Arith.round(receiveCorpus+ receiveInterest  + investOverdueFine, 2);//计算投资人总共将获取的金额
	
				//查看投资用户是否受到非法资金改动
				DataSafety investDataTamperproof = new DataSafety();
				investDataTamperproof.id = investUserId;
				boolean investIsChange = investDataTamperproof.signCheck(error);
				
				// c.填写投资账单的收款情况
				String updateSql = "update t_bill_invests set status = ?, real_receive_time = ?, real_receive_corpus = ?," +
						"  overdue_fine = ? where user_id = ? and bid_id = ? and periods = ? and status not in(?,?) and invest_id = ?";
				
		        Query query = em.createQuery(updateSql).setParameter(1, Constants.OVERDUE_RECEIVABLES).setParameter(2, DateUtil.currentDate())
		        .setParameter(3, receiveCorpus).setParameter(4, investOverdueFine)
		        .setParameter(5, investUserId).setParameter(6, this.bidId).setParameter(7, this.periods)
		        .setParameter(8, Constants.OVERDUE_RECEIVABLES).setParameter(9, Constants.FOR_DEBT_MARK).setParameter(10, investId);
		        
		        try {
					rows = query.executeUpdate();
				} catch(Exception e) {
					e.printStackTrace();
					Logger.info("填写投资账单的收款情况时："+e.getMessage());
					error.code = -5;
					error.msg = "还款出现异常，导致还款失败";
					JPA.setRollbackOnly();
					
					return error.code;
				}
				
				if(rows == 0){
					error.code = -1;
					error.msg = "还款出现异常，导致还款失败";
					JPA.setRollbackOnly();
					
					return error.code;
					
				}
				
				// b.重新计算每个投资用户获取本期还款(本金+利息)金额
				if(invest_status != Constants.ONE){
				String balanceSql = "update t_users set balance = balance + ? where id =?";
				
				Query BalanceUpdate = em.createQuery(balanceSql).setParameter(1, receiveCorpus).setParameter(2, investUserId);
				
				try {
					rows = BalanceUpdate.executeUpdate();
					
				} catch(Exception e) {
					e.printStackTrace();
					Logger.info("返回每个投资用户每期获得的投资本金以及罚息时："+e.getMessage());
					error.code = -5;
					error.msg = "还款出现异常，导致还款失败";
					JPA.setRollbackOnly();
					
					return error.code;
				}
				
				if(rows == 0){
					error.code = -1;
					error.msg = "还款出现异常，导致还款失败";
					JPA.setRollbackOnly();
					
					return error.code;
				}
				
				//d.开始添加投资人交易记录和事件
				Map<String, Double> investForDetail = DealDetail.queryUserFund(investUserId, error);
				if(error.code < 0 || investForDetail == null) {
					JPA.setRollbackOnly();
					
					return -1;
				}
				
				double investFreeze = investForDetail.get("freeze");
				double investReceiveAmount = investForDetail.get("receive_amount");
				
				Double investBalance10 = this.queryBalance(investUserId, error);
				
				DealDetail investDetail = new DealDetail(investUserId, DealType.OVER_RECEIVE, receiveCorpus,
						investBillId, investBalance10, investFreeze, investReceiveAmount, "逾期收款获取第"+this._id+"号账单投资金额");
				
				//添加交易记录
				investDetail.addDealDetail(error);
				if(error.code < 0) {
					JPA.setRollbackOnly();
					
					return -1;
				}
				
				// d.投资人添加逾期费
				String balanceSql3 = "update t_users set balance = balance + ? where id =?";
				
				Query BalanceUpdate3 = em.createQuery(balanceSql3).setParameter(1, investOverdueFine).setParameter(2, investUserId);
				
				try {
					rows = BalanceUpdate3.executeUpdate();
					
				} catch(Exception e) {
					e.printStackTrace();
					Logger.info("投资人添加逾期费："+e.getMessage());
					error.code = -5;
					error.msg = "还款出现异常，导致还款失败";
					JPA.setRollbackOnly();
					
					return error.code;
				}
				
				if(rows == 0){
					error.code = -1;
					error.msg = "还款出现异常，导致还款失败";
					JPA.setRollbackOnly();
					
					return error.code;
				}
				
				Double investBalance3 = this.queryBalance(investUserId, error);
			
				DealDetail investOverdueFeeDetail = new DealDetail(investUserId, DealType.ADD_OVERDUE_FEE, investOverdueFine,
						investBillId, investBalance3, investFreeze, investReceiveAmount, "获取第"+this._id+"号账单逾期费");
				
				//添加获取逾期费用的交易记录
				investOverdueFeeDetail.addDealDetail(error);
				if(error.code < 0) {
					JPA.setRollbackOnly();
					
					return -1;
				}
				
				// c.投资人减去管理费
				String balanceSql2 = "update t_users set balance = balance - ? where id =? and balance >= ?";
				
				Query BalanceUpdate2 = em.createQuery(balanceSql2).setParameter(1, manageFee).setParameter(2, investUserId).setParameter(3, manageFee);
				
				try {
					rows = BalanceUpdate2.executeUpdate();
					
				} catch(Exception e) {
					e.printStackTrace();
					Logger.info("投资人减去管理费："+e.getMessage());
					error.code = -5;
					error.msg = "还款出现异常，导致还款失败";
					JPA.setRollbackOnly();
					
					return error.code;
				}
				
				if(rows == 0){
					error.code = -1;
					error.msg = "还款出现异常，导致还款失败";
					JPA.setRollbackOnly();
					
					return error.code;
				}
				
				Double investBalance2 = this.queryBalance(investUserId, error);
				
				DealDetail investFeeDetail = new DealDetail(investUserId, DealType.CHARGE_INVEST_FEE, manageFee,
						investBillId, investBalance2, investFreeze, investReceiveAmount, "扣除第"+this._id+"号账单理财管理费");
				
				//添加扣除理财管理费的交易记录
				investFeeDetail.addDealDetail(error);
				if(error.code < 0) {
					JPA.setRollbackOnly();
					
					return -1;
				}
				
				
				
				
				//添加平台扣除理财管理费交易记录
				DealDetail.addPlatformDetail(DealType.INVEST_FEE, investBillId, investUserId, -1, DealType.ACCOUNT, manageFee, 1, "平台收取第"+this._id+"号账单投资管理费", error);
				if(error.code < 0) {
					JPA.setRollbackOnly();
					
					return -1;
				}
				
				/* 添加CPS推广费 */
				User.rewardCPS(investUserId, manageFee, investBillId, error);
				
				if (error.code < 0) {
					JPA.setRollbackOnly();

					return error.code;
				}
				
                Map<String, Object> userMap = new HashMap<String,Object>();
				
				String userSql = "select new Map(name as name, email as eamil, mobile as mobile) from t_users where id = ? ";
				
				try {
					userMap = t_users.find(userSql, investUserId).first();
				}catch(Exception e) {
					e.printStackTrace();
					Logger.info("根据用户id查询时："+e.getMessage());
					error.code = -1;
					error.msg = "查询用户名失败";
					JPA.setRollbackOnly();
					
					return error.code;
				}
				
				String userName = (String)userMap.get("name");
				String userEamil = (String)userMap.get("eamil");
				String userMobile = (String)userMap.get("mobile");
				
				//发送站内信 尊敬的username:\n 您投资的借款title,第repayPeriod期还款已经完成.<br/>paymentModeStr本期应得总额：
				//￥recivedSum,其中本金部分为：hasP元,利息部分：hasI元,实得逾期罚息：hasLFI元<br/>扣除投资管理费：
				//￥mFee元<br/>实得总额：￥msFee元
				TemplateStation station = new TemplateStation();
				station.id = Templets.M_INVEST_RECEIVE;
				
				if(station.status) {
					String mContent = station.content.replace("userName",userName);
					mContent = mContent.replace("title",title);
					mContent = mContent.replace("repayPeriod",this.periods+"");
					mContent = mContent.replace("recivedSum",  DataUtil.formatString(receive));
					mContent = mContent.replace("hasP",  DataUtil.formatString(receiveCorpus));
					mContent = mContent.replace("hasI",  DataUtil.formatString(receiveInterest));
					mContent = mContent.replace("bchasY",  "0.00");
					mContent = mContent.replace("hasLFI",  DataUtil.formatString(investOverdueFine));
					mContent = mContent.replace("mFee",  DataUtil.formatString(manageFee));
					mContent = mContent.replace("msFee",  DataUtil.formatString(Arith.round(receive - manageFee, 2)));
					
					StationLetter letter = new StationLetter();
					letter.senderSupervisorId = 1;
					letter.receiverUserId = investUserId;
					letter.title = station.title;
					letter.content = mContent;
					 
					letter.sendToUserBySupervisor(error);
				}
				
				//发送邮件 尊敬的username:\n [date] 您在晓风安全网贷系统6对标的【title】还款了￥needSum 元
				TemplateEmail email = new TemplateEmail();
				email.id = Templets.E_INVEST_RECEIVE;
				
				if(email.status) {
					String eContent = email.content.replace("userName", userName);
					eContent = eContent.replace("title",title);
					eContent = eContent.replace("repayPeriod",this.periods+"");
					eContent = eContent.replace("recivedSum",  DataUtil.formatString(receive));                       
					eContent = eContent.replace("hasP",  DataUtil.formatString(receiveCorpus));                       
					eContent = eContent.replace("hasI",  DataUtil.formatString(receiveInterest));                     
					eContent = eContent.replace("hasLFI",  DataUtil.formatString(investOverdueFine));                 
					eContent = eContent.replace("mFee",  DataUtil.formatString(manageFee));                           
					eContent = eContent.replace("msFee",  DataUtil.formatString(Arith.round(receive - manageFee, 2)));
					email.addEmailTask(userEamil, email.title, eContent);
				}
				
				//尊敬的userName: 投资的编号bidId借款标repayPeriod期已成功还款，回款金额￥recivedSum元，扣除管理费￥mFee元，实得总额￥msFee元
				TemplateSms sms = new TemplateSms();
				sms.id = Templets.S_RECOVER_ADVANCE_SUCCESS;
				if(sms.status && StringUtils.isNotBlank(userMobile)) {
					String sContent = sms.content.replace("userName", userName);
					sContent = sContent.replace("title",title);
					sContent = sContent.replace("repayPeriod",this.periods+"");
					sContent = sContent.replace("recivedSum",  DataUtil.formatString(receive));
					sContent = sContent.replace("hasP",  DataUtil.formatString(receiveCorpus));
					sContent = sContent.replace("hasI",  DataUtil.formatString(receiveInterest));
					sContent = sContent.replace("bchasY",  "0.00");
					sContent = sContent.replace("hasLFI",  DataUtil.formatString(investOverdueFine));
					sContent = sContent.replace("mFee",  DataUtil.formatString(manageFee));
					sContent = sContent.replace("msFee",  DataUtil.formatString(Arith.round(receive - manageFee, 2)));                                                  
					TemplateSms.addSmsTask(userMobile, sContent);
				}
				
				//如果投资用户账户资金没有遭到非法改动，那么就更新其篡改标志，否则不更新
				if(investIsChange){
					investDataTamperproof.updateSignWithLock(investUserId, error);
				} 
			   }else{
					
			 		DealDetail dealDetail = new DealDetail();
				
					/* 添加平台交易记录 */
					dealDetail.addPlatformDetail(DealType.ADD_PLATFORM_REGULAR,userId, userId, -1, 
							DealType.ACCOUNT, receiveCorpus, 1,
							"返还垫付给投资者赎回金额", error);
					
					if (error.code < 0) {
						JPA.setRollbackOnly();
						error.msg = "借款用户返还垫付给投资者赎回金额失败";
						return -1;
					}
					//用户事件添加
					DealDetail.userEvent(userId, UserEvent.ROLLBACK_SHUHUI, "返还赎回金额成功", error);
					
		    	}
			 }
			}
			//手动付款模式
			else{
				String updateInvestSql = "update t_bill_invests set status = ? where bid_id = ? and periods = ? and status not in(?,?,?,?,?,?)";
				
		        Query updateInvest = em.createQuery(updateInvestSql).setParameter(1, Constants.FOR_OVERDUE_PAY).setParameter(2, bidId)
		        .setParameter(3, period).setParameter(4, Constants.FOR_PAY).setParameter(5, Constants.FOR_OVERDUE_PAY).setParameter(6, Constants.NORMAL_RECEIVABLES)
		        .setParameter(7, Constants.ADVANCE_PRINCIIPAL_RECEIVABLES).setParameter(8, Constants.OVERDUE_RECEIVABLES).setParameter(9, Constants.FOR_DEBT_MARK);
				
				try {
					rows = updateInvest.executeUpdate();
				} catch(Exception e) {
					e.printStackTrace();
					Logger.info("修改投资账单状态时："+e.getMessage());
					error.code = -3;
					error.msg = "数据库异常，导致修改投资账单状态失败";
					JPA.setRollbackOnly();
					
					return error.code;
				}
			}
			
		}
		else{
			//6.如果为本金垫付，扣完借款人还款金额后直接添加平台收取本金垫付交易记录
			DealDetail.addPlatformDetail(DealType.ADVANCE_FEE, this._id, userId, -1, DealType.ACCOUNT, payment, 1, "平台收取第"+this._id+"号账单垫付金额", error);
			if(error.code < 0) {
				JPA.setRollbackOnly();
				
				return -1;
			}
		}
		
		//7.修改借款账单还款情况
		String updateBillSql = "update t_bills set status = ?, real_repayment_time = ?, real_repayment_corpus = ?, real_repayment_interest = ?, overdue_fine = ? where id = ? and status in (?,?)";
			
		Query updateBill = em.createQuery(updateBillSql).setParameter(1, Constants.OVERDUE_PATMENT).setParameter(2, DateUtil.currentDate())
        .setParameter(3, repaymentCorpus).setParameter(4, repaymentInterest).setParameter(5, repayOverdueFine)
        .setParameter(6, this._id).setParameter(7, Constants.NO_REPAYMENT).setParameter(8, Constants.ADVANCE_PRINCIIPAL_REPAYMENT);
		
		try {
			rows = updateBill.executeUpdate();
		} catch(Exception e) {
			e.printStackTrace();
			Logger.info("修改借款账单数据时："+e.getMessage());
			error.code = -3;
			error.msg = "还款出现异常，导致还款失败";
			JPA.setRollbackOnly();
			
			return error.code;
		}finally{
			double Userbalance = queryBalance(userId, error);//用户可用余额
			
			if(rows == 0){
				error.code = -1;
				error.msg = "还款出现异常，导致还款失败";
				JPA.setRollbackOnly();
				
				return error.code;
			}
			
			if(Userbalance < 0){
				error.code = -1;
				error.msg = "资金不足,请充值!";
				JPA.setRollbackOnly();
				
				return error.code;
			}
		}
		
		//8.判断这个借款标是否已还完款，如果还完后给这个借款标标记为已还完状态
		if(this.isEndPayment(bidId, error) == 0){
			String bidSql = "update t_bids set status = ?, last_repay_time = ? where id = ?";
		
            Query query = em.createQuery(bidSql).setParameter(1, Constants.BID_REPAYMENTS).setParameter(2, DateUtil.currentDate())
            .setParameter(3, bidId);
            
			try {
				rows = query.executeUpdate();;
			
			} catch(Exception e) {
				e.printStackTrace();
				Logger.info("修改借款标为已还款完状态时："+e.getMessage());
				error.code = -7;
				error.msg = "还款出现异常，导致还款失败";
				JPA.setRollbackOnly();
				
				return error.code;
			}
			
			if(rows == 0){
				error.code = -1;
				error.msg = "还款出现异常，导致还款失败";
				JPA.setRollbackOnly();
			
				return error.code;
			}
		}
		
		//4.扣除借款人还款本期的本金和逾期费
		String userBalanceSql = "update t_users set balance = balance - ? where id = ? and balance >= ?";
		
		Query userBalanceUpdate = em.createQuery(userBalanceSql).setParameter(1,repaymentCorpus).setParameter(2, userId).setParameter(3,repaymentCorpus);
		
		try {
			rows = userBalanceUpdate.executeUpdate();
		} catch(Exception e) {
			e.printStackTrace();
			Logger.info("修改借款人账户金额时："+e.getMessage());
			error.code = -2;
			error.msg = "还款出现异常，导致还款失败";
			JPA.setRollbackOnly();
			
			return error.code;
		}
		
		if(rows == 0){
			error.code = -1;
			error.msg = "还款出现异常，导致还款失败";
			JPA.setRollbackOnly();
			
			return error.code;
		}
		
		//9.添加有关借款账单还款的交易记录和事件
		Map<String, Double> billForDetail = DealDetail.queryUserFund(userId, error);
		if(error.code < 0 || billForDetail == null) {
			JPA.setRollbackOnly();
			
			return -1;
		}
		
		double userFreeze = billForDetail.get("freeze");
		double userReceiveAmount = billForDetail.get("receive_amount");
		
		Double investBalance15 = this.queryBalance(userId, error);
		
		DealDetail detail = new DealDetail(userId, DealType.CHARGE_OVER_PAY, repaymentCorpus,
				this._id, investBalance15, userFreeze, userReceiveAmount, "第"+this._id+"号账单逾期还款扣除还款金额");
		
		//添加逾期还款的交易记录
		detail.addDealDetail(error);
		if(error.code < 0) {
			JPA.setRollbackOnly();
			
			return -1;
		}
		
		//4.扣除借款人还款本期的逾期费用
		String userBalanceSql2 = "update t_users set balance = balance - ? where id = ? and balance >= ?";
		
		Query userBalanceUpdate2 = em.createQuery(userBalanceSql2).setParameter(1, repayOverdueFine).setParameter(2, userId).setParameter(3, repayOverdueFine);
		
		try {
			rows = userBalanceUpdate2.executeUpdate();
		} catch(Exception e) {
			e.printStackTrace();
			Logger.info("修改借款人账户金额时："+e.getMessage());
			error.code = -2;
			error.msg = "还款出现异常，导致还款失败";
			JPA.setRollbackOnly();
			
			return error.code;
		}
		
		if(rows == 0){
			error.code = -1;
			error.msg = "还款出现异常，导致还款失败";
			JPA.setRollbackOnly();
			
			return error.code;
		}
		
		//本次查询用户可用余额是获取最新状态值，避免从缓存里拿数据
		Double userBalance2 = this.queryBalance(userId, error);
		
		DealDetail overdueDetail = new DealDetail(userId, DealType.CHARGE_OVERDUE_FEE, repayOverdueFine,
				this._id, userBalance2, userFreeze, userReceiveAmount, "第"+this._id+"号账单逾期还款扣除逾期费");
		
		//添加逾期还款扣除逾期费的交易记录
		overdueDetail.addDealDetail(error);
		if(error.code < 0) {
			JPA.setRollbackOnly();
			
			return -1;
		}
		
		//逾期还款减去信用积分
		int creditScore = detail.addCreditScore(userId, -1, 0, this._id, "第"+this._id+"号账单逾期还款扣除信用积分", error);
		if(error.code < 0) {
			JPA.setRollbackOnly();
			
			return -1;
		}
		
		//更新用户信用等级
		/* 
		 * v.7.2.7不及时更新用户信用等级，每天凌晨统一更新
		User.updateCreditLevel(userId, error);
		if (error.code < 0) {
			JPA.setRollbackOnly();
			
			return error.code;
		}
		*/
		
		//添加事件
		DealDetail.userEvent(userId, UserEvent.ADD_NOMAL_PAY, "第"+this._id+"号账单用户逾期还款", error);
		if(error.code < 0) {
			JPA.setRollbackOnly();
			
			return -1;
		}
		
        Map<String, Object> userMap = new HashMap<String,Object>();
		
		String userSql = "select new Map(name as name, email as eamil, mobile as mobile) from t_users where id = ? ";
		
		try {
			userMap = t_users.find(userSql, userId).first();
		}catch(Exception e) {
			e.printStackTrace();
			Logger.info("根据用户id查询时："+e.getMessage());
			error.code = -1;
			error.msg = "查询用户名失败";
			
			return error.code;
		}
		
		String userName = (String)userMap.get("name");
		String userEamil = (String)userMap.get("eamil");
		String userMobile = (String)userMap.get("mobile");
		
		//发送站内信  尊敬的username:\n [date] 您在晓风安全网贷系统6对标的【title】还款了￥needSum 元
		TemplateStation station = new TemplateStation();
		station.id = Templets.M_SUCCESS_PAY;
		BackstageSet backstageSet = BackstageSet.getCurrentBackstageSet();
		
		if(station.status) {
			String mContent = station.content.replace("userName", userName);
			mContent = mContent.replace("date",DateUtil.dateToString(new Date()));
			mContent = mContent.replace("bidId",backstageSet.loanNumber+bidId);
			mContent = mContent.replace("repayPeriod",this.periods+"");
			mContent = mContent.replace("needSum",  DataUtil.formatString(payment));
			mContent = mContent.replace("rcp",DataUtil.formatString(repaymentCorpus));
			mContent = mContent.replace("hasI",DataUtil.formatString(repaymentInterest));
			mContent = mContent.replace("rodf",DataUtil.formatString(repayOverdueFine));
			mContent = mContent.replace("creditScore",-creditScore+"");
			
			StationLetter letter = new StationLetter();
			letter.senderSupervisorId = 1;
			letter.receiverUserId = userId;
			letter.title = station.title;
			letter.content = mContent;
			 
			letter.sendToUserBySupervisor(error);
		}
		
		//发送邮件 尊敬的username:\n [date] 您在晓风安全网贷系统6对标的【title】还款了￥needSum 元
		TemplateEmail email = new TemplateEmail();
		email.id = Templets.E_SUCCESS_PAY;

		if(email.status) {
			String eContent = email.content.replace("userName", userName);
			eContent = eContent.replace("date",DateUtil.dateToString(new Date()));
			eContent = eContent.replace("title",title);
			eContent = eContent.replace("needSum",  DataUtil.formatString(payment));
			eContent = eContent.replace("creditScore",-creditScore+"");
			email.addEmailTask(userEamil, email.title, eContent);
		}
		
		//发送短信  尊敬的userName: 你申请的编号bidId借款标repayPeriod期成功还款，还款金额￥needSum元，
		TemplateSms sms = new TemplateSms();
	
		sms.id = Templets.S_REPAY_SUCCESS;
		if(sms.status && StringUtils.isNotBlank(userMobile)) {
			String eContent = sms.content.replace("userName", userName);
			eContent = eContent.replace("bidId",backstageSet.loanNumber+bidId);
			eContent = eContent.replace("repayPeriod",this.periods+"");
			eContent = eContent.replace("needSum",DataUtil.formatString(payment));
			eContent = eContent.replace("rcp",DataUtil.formatString(repaymentCorpus));
			eContent = eContent.replace("hasI",DataUtil.formatString(repaymentInterest));
			eContent = eContent.replace("rodf",DataUtil.formatString(repayOverdueFine));

			TemplateSms.addSmsTask(userMobile, eContent);
		}
		
		//10.更改当前用户在缓存变动的数据
		/*User user = User.currUser();
		user.balance = lastBalance;
		user.creditScore = user.creditScore + creditScore;
		user.setCurrUser(user);*/
		
		//11.还款完毕后重新生成新的数据库防篡改标志
		DataSafety dataTamperproof = new DataSafety();
		dataTamperproof.updateSignWithLock(userId, error);
		
		error.code = 0;
		error.msg = "还款成功";
		return error.code;
 	}
 	
 	/**
 	 * 判断借款账单是否已还完款
 	 * @param bidId
 	 * @param error
 	 * @return
 	 */
 	public int isEndPayment(long bidId, ErrorInfo error){
 		error.clear();
 		Object count = 0;
 		String sql = "select count(1) from t_bills where bid_id = ? and status = ?";
 		Query query = JPA.em().createNativeQuery(sql);
 		query.setParameter(1, bidId);
 		query.setParameter(2, Constants.ADVANCE_PRINCIIPAL_REPAYMENT);
 		
 		try {
 			count = query.getResultList().get(0);
		} catch (Exception e) {
			return -1;
		}
		
		if(null == count || Integer.parseInt(count.toString()) > 0) {
			return -1;
		}
		
		query = JPA.em().createNativeQuery(sql);
 		query.setParameter(1, bidId);
 		query.setParameter(2, Constants.NO_REPAYMENT);
		
 		try {
 			count = query.getResultList().get(0);
		} catch(Exception e) {
			return -1;
		}
			
		if(null == count) {
			return -1;
		}
		
 		return Integer.parseInt(count.toString());
 	}
 	
 	/**
 	 * 校验是否本金垫付已经垫付完了
 	 * @param bidId
 	 * @return
 	 */
 	public int isEndPayment(long bidId){
 		int count = 0;
 		
 		try {
 			count = (int)t_bills.count("status = ? and bid_id = ?", Constants.NO_REPAYMENT, bidId);
		} catch(Exception e) {
			return -1;
		}
			
 		return count;
 	}
 	
 	/**
 	 * 标记逾期
 	 * @param obj
 	 */
 	public static int markOverdue(long supervisorId, long billIdStr, ErrorInfo error){
 		error.clear();
 		
 		long billId = billIdStr;
 		
 		EntityManager em = JPA.em();
 		Query query = em.createQuery("update t_bills set overdue_mark = ?, mark_overdue_time = ? where id = ? and overdue_mark not in(?) ").
 		setParameter(1, Constants.BILL_OVERDUE).setParameter(2, new Date()).setParameter(3, billId).setParameter(4, Constants.BILL_OVERDUE);
 		int rows = 0;
 		
 		try {
 			rows = query.executeUpdate();
		} catch(Exception e) {
			e.printStackTrace();
			Logger.info("管理员手动标记借款账单为逾期时："+e.getMessage());
			error.code = -1;
			error.msg = "数据库异常，导致管理员手动标记借款账单为逾期失败";
			
			return error.code;
		}
		
		if(rows == 0){
			error.code = -1;
			error.msg = "标记逾期操作没有执行";
			JPA.setRollbackOnly();
			
			return error.code;
			
		}
 	  
		//添加管理员操作日志
		DealDetail.supervisorEvent(supervisorId, SupervisorEvent.MAKE_BILL_OVER, "标记"+billId+"号账单为逾期", error);
		if(error.code < 0) {
			JPA.setRollbackOnly();
			
			return -1;
		}
		
		error.code = 0;
 		error.msg = "标记逾期成功！";
		
		return error.code;
		
 	}
 	
 	/**
 	 * 系统标记逾期或重新计算逾期费用(每天凌晨00:00系统运行一次该方法)
 	 */
 	public int systemMakeOverdue(ErrorInfo error){
 		error.clear();
		EntityManager em = JPA.em();

		// 1.修改逾期的借款账单状态和时间
		String updateBillSql = "UPDATE t_bills SET mark_overdue_time = now(), overdue_mark = - 1 WHERE status = - 1 AND overdue_mark = 0 AND DATEDIFF(now(), repayment_time) > 0";
		Query updateBill = em.createNativeQuery(updateBillSql);
		try {
			updateBill.executeUpdate();
		} catch (Exception e) {
			e.printStackTrace();
			Logger.info("修改借款账单逾期生成的数据时：" + e.getMessage());
			
			error.code = -1;
			error.msg = "数据库异常，导致修改借款账单逾期生成的数据失败";
			JPA.setRollbackOnly();

			return error.code;
		}

		  
		  //2.修改借款账单逾期生成的逾期费用数据
		  String updateBillSql2 = "update t_bills t1,(select c.id, round((c.days * c.amount * c.overdue_rate/100),2)" +
		  		" overdue_fee from (select a.id, b.overdue_rate, DATEDIFF(now(), a.repayment_time) days, " +
		  		"(a.repayment_corpus + a.repayment_interest) amount from t_bills a join t_bids b ON a.bid_id = b.id " +
		  		"where a.`status` in(-1,-2) and a.overdue_mark in(-1,-2,-3) group by a.id) c) t2 set t1.overdue_fine =" +
		  		" t2.overdue_fee where t1.id = t2.id";
		  
		  Query updateBill2 = em.createNativeQuery(updateBillSql2);
		    
		  try {
			  updateBill2.executeUpdate();
		  } catch(Exception e) {
				e.printStackTrace();
				Logger.info("修改借款账单逾期生成的数据时："+e.getMessage());
				error.code = -3;
				error.msg = "数据库异常，导致修改借款账单逾期生成的数据失败";
				JPA.setRollbackOnly();
					
				return error.code;
			}
		  
		  /* 2015-1-7限制逾期费超过利息的2.5倍 */
		  if(Constants.IS_STINT_OF && Bill.countOverdueFine() < 1) {
			  JPA.setRollbackOnly();
			  
			  return -1;
		  }
		  
		//2.修改理财账单逾期生成的数据
//		  String updateInvestBillSql = "update t_bill_invests t1,(select t3.id, round((t4.overdue_fine * ifnull(t6.amount,0) / t5.amount), 2)" +
//		  		" check_fine, t3.periods, t3.bid_id from (select b.bid_id, b.periods, b.overdue_fine from t_bills b where " +
//		  		"b.overdue_mark = -1 and date_format(b.mark_overdue_time, '%y%m%d') = date_format(now(), '%y%m%d')) t4 " +
//		  		"left join (select a.invest_id, a.id, a.periods, a.bid_id, a.user_id from t_bill_invests a) t3 on t3.bid_id = t4.bid_id " +
//		  		"and t3.periods = t4.periods left join (select c.id, c.amount from t_bids c) t5 on t3.bid_id = t5.id " +
//		  		"left join (select d.id, d.bid_id, d.user_id, d.amount from t_invests d) t6 on t3.bid_id = t6.bid_id and " +
//		  		"t3.user_id = t6.user_id and t3.invest_id = t6.id) t2 set t1.overdue_fine = check_fine, t1.status = -2 where t1.id = t2.id";
		  
		  String updateInvestBillSql = "update t_bill_invests t1,(select t3.id, round((t4.overdue_fine * ifnull(t6.amount,0) / t5.amount), 2)" +
	  		" check_fine, t3.periods, t3.bid_id from (select b.bid_id, b.periods, b.overdue_fine from t_bills b where " +
	  		"b.overdue_mark in(-1,-2,-3) and b.status in(-1,-2)) t4 left join (select a.invest_id, a.id, a.periods, a.bid_id, " +
	  		"a.user_id from t_bill_invests a where a.status in(-1, -2)) t3 on t3.bid_id = t4.bid_id and t3.periods = t4.periods left join " +
	  		"(select c.id, c.amount from t_bids c) t5 on t3.bid_id = t5.id left join (select d.id, d.bid_id, d.user_id, " +
	  		"d.amount from t_invests d) t6 on t3.bid_id = t6.bid_id and t3.user_id = t6.user_id and t3.invest_id = t6.id) t2 set " +
	  		"t1.overdue_fine = check_fine, t1.status = -2 where t1.id = t2.id";
		  Query updateInvestBill = em.createNativeQuery(updateInvestBillSql);
		    
		  try {
			  updateInvestBill.executeUpdate();
		  } catch(Exception e) {
				e.printStackTrace();
				Logger.info("修改理财账单逾期生成的数据时："+e.getMessage());
				error.code = -3;
				error.msg = "数据库异常，导致修改理财账单逾期生成的数据失败";
				JPA.setRollbackOnly();
					
				return error.code;
			}
		
		//罚息纠编
		String sql = "update t_bills as a,( "
				+ "select d.overdue_fine as overdue_fine,d.bid_id as bid_id,d.periods as periods "
				+ "from (select SUM(a.overdue_fine) as overdue_fine,a.bid_id as bid_id,a.periods as periods from t_bill_invests as a where a.status <> -7 GROUP BY bid_id,periods) as d " 
				+ "left join t_bills as e on d.bid_id = e.bid_id and d.periods = e.periods and e.`status` in(-1,-2) and e.overdue_mark in(-1,-2,-3) where e.overdue_fine != d.overdue_fine "
				+ ")as b set a.overdue_fine = b.overdue_fine where a.periods = b.periods and a.bid_id = b.bid_id ";
		Query query = em.createNativeQuery(sql);

		try {
			query.executeUpdate();
		} catch (Exception e) {
			Logger.info("罚息纠编：" + e.getMessage());
			error.code = -3;
			error.msg = "数据库异常，导致罚息纠编失败";
			JPA.setRollbackOnly();

			return error.code;
		}
		
		  error.code = 0;
		  return error.code;
 	}
 	
 	/**
 	 *  2015-1-6 限制逾期费的上限 
 	 */
 	public static int countOverdueFine() {
 		EntityManager em = JPA.em();
		String sql = "select bid_id from t_bills where status in(-1,-2) and overdue_mark in (-1, -2, -3) group by bid_id";
		Query query = em.createNativeQuery(sql);
		List<Object> ids = null; // 逾期标记总和
		  
		try {
			ids = query.getResultList();
		} catch (Exception e) {
			Logger.info("限制逾期费的上限，错误MSG001");
			
			return -1;
		}
		
		if(null == ids || ids.size() == 0 || null == ids.get(0)){
			Logger.info("限制逾期费的上限，错误MSG002");
			
			return -1;
		}
		
		DecimalFormat formater = new DecimalFormat();
        formater.setMaximumFractionDigits(2);
        formater.setGroupingSize(0);
        formater.setRoundingMode(RoundingMode.UP); // 不四舍五入
        
		Double sumInterest = 0d; // 利息总和
		Double sumOverdueFine = 0d; // 罚息总和
		Double sumOverdueFineN = 0d; // 未还罚息总和
		List<Object[]> overdueFine = null;
		double remain = 0; // 剩余的钱
		double sumInterestScale = 0;
		
		String sql1 = "select SUM(repayment_interest) from t_bills where bid_id = ?";
		String sql2 = "select SUM(overdue_fine) from t_bills where bid_id = ?";
		String sql3 = "select id, overdue_fine from t_bills where bid_id = ? and status in(-1,-2) and overdue_mark in (-1, -2, -3)";
		String sql4 = "update t_bills set overdue_fine = overdue_fine - ? where id = ?";
		String sql5 = "select SUM(overdue_fine) from t_bills where bid_id = ? and status in(-1,-2) and overdue_mark in (-1, -2, -3)";
		long bidId = 0;
		
		/* 已标的为主，循环处理 */
		for (Object id : ids) {
			bidId = Long.parseLong(id.toString());
			
			try {
				sumInterest = t_bills.find(sql1, bidId).first();
				sumOverdueFine = t_bills.find(sql2, bidId).first();
			} catch (Exception e) {
				Logger.info("限制逾期费的上限，错误MSG003");
				
				return -1;
			}
			
			if(null == sumInterest || null == sumOverdueFine) {
				Logger.info("限制逾期费的上限，错误MSG004");
				
				return -1;
			}
			
			/* 如果超出了利息的2.5倍 */
			sumInterestScale = sumInterest * Constants.OF_AMOUNT;
			
			if(sumOverdueFine <= sumInterestScale)
				continue ;
			
			remain = sumOverdueFine - sumInterestScale; // 求出剩下的那点钱
			
			query = em.createNativeQuery(sql3);
			query.setParameter(1, bidId);
			
			try {
				sumOverdueFineN = t_bills.find(sql5, bidId).first(); 
				overdueFine = query.getResultList(); // 以标为单位得出未还逾期中的账单列表
			} catch (Exception e) {
				Logger.info("限制逾期费的上限，错误MSG005");
				
				return -1;
			}
			
			double amount = 0;
			long billId = 0;
			double of = 0;
			
			/* 按比例扣除账单中的逾期罚息 */
			for (Object[] obj : overdueFine) {
				if(null == obj[0] || null == obj [1]){
					Logger.info("限制逾期费的上限，错误MSG006");
					
					return -1;
				}
				
				billId = Long.parseLong(obj[0].toString()); // 得到账单ID
				
				if(billId <= 0) {
					Logger.info("限制逾期费的上限，错误MSG007");
					
					return -1;
				}
				
				amount = Double.parseDouble(obj[1].toString()); // 得到这条记录的逾期费
				of = amount / sumOverdueFineN * remain; // 根据比例求出需要扣除的罚息
		        
				if(amount <= 0 || of <= 0) 
					continue ;
				
				of = Double.parseDouble(formater.format(of)); // 不四舍五入向上取整，这样就不会少扣了，且不用纠偏
				int row = 0;
				query = em.createNativeQuery(sql4);
				query.setParameter(1, of); 
				query.setParameter(2, billId);
				
				try {
					row = query.executeUpdate(); // 减去多出的逾期罚息
				} catch (Exception e) {
					Logger.info("限制逾期费的上限，错误MSG008");
					
					return -1;
				}
				
				if(row < 1) {
					Logger.info("限制逾期费的上限，错误MSG009");
					
					return -1;
				}
			}
		}
		
		return 1;
 	}
 	
 	/**
 	 * 标记坏账
 	 * @param obj
 	 */
 	public static int markBad(long supervisorId, long billId, ErrorInfo error){
 		error.clear();
 		EntityManager em = JPA.em();
 		
 		String sql = "update t_bills bill set bill.overdue_mark = ?, mark_bad_time = ? where bill.id = ? and bill.overdue_mark not in(?)";
 		Query query = em.createQuery(sql).setParameter(1, Constants.BILL_BAD_DEBTS).
  	    setParameter(2, DateUtil.currentDate()).setParameter(3, billId).setParameter(4, Constants.BILL_BAD_DEBTS);
 		int rows = 0;
  	    
  	   try {
 			rows = query.executeUpdate();
 		} catch(Exception e) {
 			e.printStackTrace();
			Logger.info("修改借款账单数据时："+e.getMessage());
			error.code = -1;
			error.msg = "数据库异常，导致修改借款账单数据失败";
			
			return 0;
 		}
 		
 		if(rows == 0){
			error.code = -1;
			error.msg = "修改借款账单数据操作没有执行";
			JPA.setRollbackOnly();
			
			return error.code;
			
		}
		
 		//添加管理员操作日志
		DealDetail.supervisorEvent(supervisorId, SupervisorEvent.MAKE_BILL_BAD, "标记账单为坏账", error);
		if(error.code < 0) {
			JPA.setRollbackOnly();
			
			return -1;
		}
 		
		error.code = 0;
		error.msg = "标记坏账成功";
		return error.code;
 	}
 	
 	/**
 	 * 用户的待还金额
 	 * @return
 	 */
	public static double forPay(long userId, ErrorInfo error) {
		String sql = "select SUM(a.repayment_corpus + a.repayment_interest + a.overdue_fine) "
				+ "as totalpay from t_bills as a, t_bids as b where a.bid_id = b.id and b.user_id = ? and a.status in (-1,-2)";
		Double pay = 0.0;
		
		try {
			pay = t_products.find(sql, userId).first();
		} catch (Exception e) {
			Logger.info("用户的待还金额:" + e.getMessage());
			error.code = -1;
			error.msg = "查询用户待还金额出现异常";
			JPA.setRollbackOnly();
			
			return -1;
		}
		
		if(null == pay || pay == 0){
			pay = 0.0;
		}
		
		return pay;
	}
 	
 	/**
 	 * 用户的待收金额
 	 * @return
 	 */
	public static double forReceive(long userId, ErrorInfo error) {
		String sql = "select SUM(a.receive_corpus + a.receive_interest + IFNULL(a.overdue_fine,0.00))"
				+ " as totalreceive from t_bill_invests as a where a.user_id = ? and a.status in (-1,-2,-5,-6)";
		Double receive = 0.0;
		
		try {
			receive = t_bill_invests.find(sql, userId).first();
		} catch (Exception e) {
			Logger.info("用户的待收金额:" + e.getMessage());
			error.code = -1;
			error.msg = "查询用户待还金额出现异常";
			JPA.setRollbackOnly();
			
			return -1;
		}
		
		if(null == receive || receive == 0){
			receive = 0.0;
		}
		
		return receive;
	}
     
 	/**
 	 * 查询借款账单信息并计算借款人实际的逾期罚款
 	 * @return
 	 * @throws ParseException
 	 */
 	public Map<String,Object> billOverdueFee(long billId, ErrorInfo error){
 		error.clear();
 		
		Map<String,Object> map = new HashMap<String,Object>();
		Map<String, Object> billMap = new HashMap<String,Object>();
 		
 		String billSql = "select new Map(bill.repayment_corpus as repayment_corpus, bill.repayment_interest as" +
			" repayment_interest, bill.repayment_time as repayment_time) from t_bills as bill where bill.id = ?";
 		
 		try {
 			billMap = t_bills.find(billSql, billId).first();
		 } catch(Exception e) {
				e.printStackTrace();
				Logger.info("查询借款账单信息时："+e.getMessage());
				error.code = -1;
				error.msg = "数据库异常，导致查询借款账单信息失败";
				JPA.setRollbackOnly();
				
				return null;
			}
		 
		double payCorpus = (Double)billMap.get("repayment_corpus");
		double payInterest = (Double)billMap.get("repayment_interest");
		Date payTime = (Date)billMap.get("repayment_time");
		
		double overdueRates = Bid.queryRateByBillId(billId, 2);
		
		if(overdueRates != 0){
			overdueRates = overdueRates / 100;
		}
		
		int days = DateUtil.daysBetween(payTime, DateUtil.currentDate());
		double payOverdueFee = Arith.mul(Arith.mul(Arith.add(payCorpus, payInterest), overdueRates), days);//借款人本期应还的逾期罚息
		
		map.put("repayment_corpus", payCorpus);
		map.put("repayment_interest", payInterest);
		map.put("payOverdueFee", payOverdueFee);
		
		return map;
 	}
 	/**
 	 * 本金垫付账单
 	 * @param supervisorId 操作员id
 	 * @param billId 账单id
 	 * @param error
 	 * @return
 	 */
 	public int principalAdvancePayment(long supervisorId, long billId, ErrorInfo error){
 		error.clear();
 		Map<String, Object> billMap = new HashMap<String,Object>();
 		 int rows = 0;
 		
 		List<Map<String, Object>> investList = new ArrayList<Map<String,Object>>();
 		EntityManager em = JPA.em();
 		
 		String billSql = "select new Map(bill.status as status, bill.bid_id as bid_id, bill.periods as periods, bill.repayment_corpus as repayment_corpus, bill.repayment_interest as" +
		" repayment_interest, bill.repayment_time as repayment_time, bill.overdue_fine as overdue_fine) from t_bills as bill where bill.id = ? and bill.status not in (?,?,?)";
		
		try {
			billMap = t_bills.find(billSql, billId, Constants.NORMAL_REPAYMENT, 
					Constants.ADVANCE_PRINCIIPAL_REPAYMENT, Constants.OVERDUE_PATMENT).first();
	 } catch(Exception e) {
			e.printStackTrace();
			Logger.info("查询借款账单信息时："+e.getMessage());
			error.code = -1;
			error.msg = "数据库异常，导致本金垫付账单失败";
			JPA.setRollbackOnly();
			
			return error.code;
		}
		
		if(null == billMap){
			error.code = -1;
			error.msg = "数据库异常，导致本金垫付账单失败";
			JPA.setRollbackOnly();
			
			return error.code;
		}
		
		int status = (Integer) billMap.get("status");
		long bidId = (Long) billMap.get("bid_id");
		int period = (Integer) billMap.get("periods");
		
 		if(status == Constants.ADVANCE_PRINCIIPAL_REPAYMENT){
 			error.code = -1;
			error.msg = "本期账单已还款";
			
			return error.code;
 		}
 		
 		//3.判断还款的借款标有没有债权在转让，如果有，将其状态回归原态,并解冻竞拍者资金
		int resulta = Debt.judgeHasBidTransfer(bidId);
		
		if(resulta < 0){
			error.code = -7;
			error.msg = "本金垫付出现异常";
			
			return error.code;
		}
 		
 		double payCorpus = (Double)billMap.get("repayment_corpus");
 		double payInterest = (Double)billMap.get("repayment_interest");
 		
 		double investmentRate = Bid.queryInvestRate(bidId);
 		
 		if(investmentRate != 0){
 			investmentRate = investmentRate /100;
 		}
 		
 		String sql = "select new Map(invest.id as id, invest.invest_id as investId, invest.receive_corpus as receive_corpus,invest.receive_interest as " +
 				"receive_interest, invest.overdue_fine as overdue_fine, invest.user_id as user_id, invest.overdue_fine) "
			+ "from t_bill_invests as invest where invest.bid_id = ? and invest.periods = ? and invest.status not in (?,?,?,?)";
 		try {
 			investList = t_bill_invests.find(sql, bidId, period, Constants.FOR_DEBT_MARK, Constants.NORMAL_RECEIVABLES, 
					Constants.ADVANCE_PRINCIIPAL_RECEIVABLES, Constants.OVERDUE_RECEIVABLES).fetch();
		 } catch(Exception e) {
			e.printStackTrace();
			Logger.info("查询投资账单信息时："+e.getMessage());
			error.code = -1;
			error.msg = "数据库异常，导致本金垫付账单失败";
			JPA.setRollbackOnly();
			
			return error.code;
		}
 		
 		if(null == investList){
			error.code = -1;
			error.msg = "数据库异常，导致本金垫付账单失败";
			JPA.setRollbackOnly();
			
			return error.code;
		}
	
		for (Map<String, Object> map : investList) {
			long investBillId = (Long) map.get("id");
			long investId = (Long) map.get("investId");
			double receiveInterest = (Double) map.get("receive_interest");// 本期的投资利息
			double receiveCorpus = (Double) map.get("receive_corpus");
			double receiveFees = (Double) map.get("overdue_fine");
			long investUserId = (Long) map.get("user_id");
			String investSql = "select balance from t_users where id = ? ";
			Double investerBalance = null;
			
			try {
				investerBalance = t_users.find(investSql, investUserId).first();//获取投资用户的余额
			 } catch(Exception e) {
					e.printStackTrace();
					Logger.info("查询投资用户的可用金额时："+e.getMessage());
					error.code = -2;
					error.msg = "数据库异常，导致本金垫付账单失败";
					JPA.setRollbackOnly();
					
					return error.code;
				}
			
			investerBalance = null == investerBalance ? 0 : investerBalance.doubleValue();
			double investManageFee = BillInvests.getInvestManagerFee(receiveInterest, investmentRate, investUserId);// 投资管理费
			double receive = Arith.round(receiveCorpus + receiveInterest + receiveFees, 2);
			
			//查看投资用户是否受到非法资金改动
			DataSafety investDataTamperproof = new DataSafety();
			investDataTamperproof.id = investUserId;
			boolean investIsChange = investDataTamperproof.signCheck(error);
			
			// 改变投资账单的收款状态为本金垫付
		    String updateSql = "update t_bill_invests set status = ?, real_receive_time = ?, real_receive_corpus = ?," +
		  		" overdue_fine = ?, real_receive_interest = ? where user_id = ? and bid_id = ? and periods = ? and invest_id = ?";
		  
	        Query query = em.createQuery(updateSql).setParameter(1, Constants.ADVANCE_PRINCIIPAL_RECEIVABLES).setParameter
	        (2, DateUtil.currentDate()).setParameter(3, receiveCorpus).setParameter(4, receiveFees).setParameter(5, receiveInterest - investManageFee)
	        .setParameter(6, investUserId).setParameter(7, bidId).setParameter(8, period).setParameter(9, investId);
	      
	        try {
				rows = query.executeUpdate();
		    } catch(Exception e) {
				e.printStackTrace();
				Logger.info("修改投资账单的状态时："+e.getMessage());
				error.code = -3;
				error.msg = "数据库异常，导致本金垫付账单失败";
				JPA.setRollbackOnly();
				
				return error.code;
			}
		  
		    if(rows == 0){
				error.code = -1;
				error.msg = "数据库异常，导致本金垫付账单失败";
				JPA.setRollbackOnly();
				
				return error.code;
				
			}
		  
			// 返回每个投资用户每期获得的投资本金和利息
			String userBalanceSql = "update t_users set balance = balance + ? where id = ?";
			Query userBalance = em.createQuery(userBalanceSql).setParameter(1, receiveInterest + receiveCorpus).setParameter(2, investUserId);
			
			 try {
				 rows = userBalance.executeUpdate();
			 } catch(Exception e) {
					e.printStackTrace();
					Logger.info("修改投资用户的金额时："+e.getMessage());
					error.code = -2;
					error.msg = "数据库异常，导致本金垫付账单失败";
					
					return error.code;
				}
			 
			 if(rows == 0){
					error.code = -1;
					error.msg = "数据库异常，导致本金垫付账单失败";
					JPA.setRollbackOnly();
					
					return error.code;
				}
			 
			Map<String, Double> billForDetail = DealDetail.queryUserFund(investUserId, error);
			if(error.code < 0 || billForDetail == null) {
				JPA.setRollbackOnly();
				
				return -1;
			}
			
			double userFreeze = billForDetail.get("freeze");
			double userReceiveAmount = billForDetail.get("receive_amount");
			Double investBalance10 = this.queryBalance(investUserId, error);
			
			DealDetail investDetail = new DealDetail(investUserId, DealType.OVER_RECEIVE, receive - receiveFees,
					investBillId, investBalance10, userFreeze, userReceiveAmount, "逾期收款获取第"+billId+"号账单投资金额");
			
			//添加逾期收款的交易记录
			investDetail.addDealDetail(error);
			if(error.code < 0) {
				JPA.setRollbackOnly();
				
				return -1;
			}
			
			// 返回每个投资用户每期获得的罚息
			String userBalanceSql2 = "update t_users set balance = balance + ? where id = ?";
			Query userBalance2 = em.createQuery(userBalanceSql2).setParameter(1, receiveFees).setParameter(2, investUserId);
			
			 try {
				 rows = userBalance2.executeUpdate();
			 } catch(Exception e) {
					e.printStackTrace();
					Logger.info("修改投资用户的金额时："+e.getMessage());
					error.code = -2;
					error.msg = "数据库异常，导致本金垫付账单失败";
					
					return error.code;
				}
			 
			 if(rows == 0){
					error.code = -1;
					error.msg = "数据库异常，导致本金垫付账单失败";
					JPA.setRollbackOnly();
					
					return error.code;
				}
			 
			Double investBalance3 = this.queryBalance(investUserId, error);
			DealDetail investOverdueFeeDetail = new DealDetail(investUserId, DealType.ADD_OVERDUE_FEE, receiveFees,
					investBillId, investBalance3, userFreeze, userReceiveAmount, "获取第"+billId+"号账单逾期费");
			
			//添加获取逾期费用的交易记录
			investOverdueFeeDetail.addDealDetail(error);
			if(error.code < 0) {
				JPA.setRollbackOnly();
				
				return -1;
			}
			
			// 减去每个投资用户每期的管理费
			String userBalanceSql3 = "update t_users set balance = balance - ? where id = ? and balance >= ?";
			Query userBalance3 = em.createQuery(userBalanceSql3).setParameter(1, investManageFee).setParameter(2, investUserId).setParameter(3, investManageFee);
			
			 try {
				 rows = userBalance3.executeUpdate();
			 } catch(Exception e) {
					e.printStackTrace();
					Logger.info("修改投资用户的金额时："+e.getMessage());
					error.code = -2;
					error.msg = "数据库异常，导致本金垫付账单失败";
					
					return error.code;
				}
			 
			 if(rows == 0){
					error.code = -1;
					error.msg = "数据库异常，导致本金垫付账单失败";
					JPA.setRollbackOnly();
					
					return error.code;
				}
			 
			Double investBalance4 = this.queryBalance(investUserId, error);
			
			DealDetail investFeeDetail = new DealDetail(investUserId, DealType.CHARGE_INVEST_FEE, investManageFee,
					investBillId, investBalance4, userFreeze, userReceiveAmount, "扣除"+billId+"号 账单理财管理费");
			
			//添加扣除理财管理费的交易记录
			investFeeDetail.addDealDetail(error);
			if(error.code < 0) {
				JPA.setRollbackOnly();
				
				return -1;
			}
			
			
			//添加平台扣除理财管理费交易记录
			DealDetail.addPlatformDetail(DealType.INVEST_FEE, investBillId, investUserId, -1, DealType.ACCOUNT, investManageFee, 1, "平台收取"+billId+"号 账单投资管理费", error);
			if(error.code < 0) {
				JPA.setRollbackOnly();
				
				return -1;
		    }
		 
			//添加平台本金垫付支出交易记录
			DealDetail.addPlatformDetail(DealType.ADVANCE_FEE, billId, -1, investUserId, DealType.ACCOUNT, (receiveInterest + receiveCorpus + receiveFees), 2, "平台本金垫付"+billId+"号 账单支出", error);
			if(error.code < 0) {
				JPA.setRollbackOnly();
				
				return -1;
			}
			
			Map<String, Object> userMap = new HashMap<String,Object>();
				
			String userSql = "select new Map(name as name, email as eamil, mobile as mobile) from t_users where id = ? ";
			
			try {
				userMap = t_users.find(userSql, investUserId).first();
			}catch(Exception e) {
				e.printStackTrace();
				Logger.info("根据用户id查询时："+e.getMessage());
				error.code = -1;
				error.msg = "查询用户名失败";
				
				return error.code;
			}
			
			String bidSql = "select title from t_bids where id = ? ";
			String title = null;
			
			try {
				title = t_bids.find(bidSql, bidId).first();
			}catch(Exception e) {
				e.printStackTrace();
				Logger.info("根据用户id查询时："+e.getMessage());
				error.code = -1;
				error.msg = "查询用户名失败";
				
				return error.code;
			}
			
			String userName = (String)userMap.get("name");
			String userEamil = (String)userMap.get("eamil");
			String userMobile = (String)userMap.get("mobile");
			
			//发送站内信 尊敬的username:\n 您投资的借款title,第repayPeriod期还款已经完成.<br/>paymentModeStr本期应得总额：
			//￥recivedSum,其中本金部分为：hasP元,利息部分：hasI元,实得逾期罚息：hasLFI元<br/>扣除投资管理费：
			//￥mFee元<br/>实得总额：￥msFee元
			TemplateStation station = new TemplateStation();
			
			station.id = Templets.M_INVEST_RECEIVE;
			if(station.status) {
				String mContent = station.content.replace("userName",userName);
				mContent = mContent.replace("title",title);
				mContent = mContent.replace("repayPeriod",period+"");
				mContent = mContent.replace("recivedSum",  DataUtil.formatString(receive));
				mContent = mContent.replace("hasP",  DataUtil.formatString(receiveCorpus));
				mContent = mContent.replace("hasI",  DataUtil.formatString(receiveInterest));
				mContent = mContent.replace("bchasY",  "0.00");
				mContent = mContent.replace("hasLFI",  DataUtil.formatString(receiveFees));
				mContent = mContent.replace("mFee",  DataUtil.formatString(investManageFee));
				mContent = mContent.replace("msFee",  DataUtil.formatString(Arith.round(receive - investManageFee, 2)));
				
				StationLetter letter = new StationLetter();
				letter.senderSupervisorId = 1;
				letter.receiverUserId = investUserId;
				letter.title = station.title;
				letter.content = mContent;
				 
				letter.sendToUserBySupervisor(error);
			}
			
			//发送邮件 尊敬的username:\n [date] 您在晓风安全网贷系统6对标的【title】还款了￥needSum 元
			TemplateEmail email = new TemplateEmail();
			email.id = Templets.E_INVEST_RECEIVE;
			
			if(email.status) {
				String eContent = email.content.replace("userName", userName);
				eContent = eContent.replace("title",title);
				eContent = eContent.replace("repayPeriod",period+"");
				eContent = eContent.replace("recivedSum",  DataUtil.formatString(receive));                                
				eContent = eContent.replace("hasP",  DataUtil.formatString(receiveCorpus));                                
				eContent = eContent.replace("hasI",  DataUtil.formatString(receiveInterest));                              
				eContent = eContent.replace("hasLFI",  DataUtil.formatString(receiveFees)); 
				eContent = eContent.replace("bchasI",  "0.00");
				eContent = eContent.replace("mFee",  DataUtil.formatString(investManageFee));                              
				eContent = eContent.replace("msFee",  DataUtil.formatString(Arith.round(receive - investManageFee, 2)));   
				email.addEmailTask(userEamil, email.title, eContent);
			}
		  
			//尊敬的userName: 投资的编号bidId借款标repayPeriod期已成功还款，回款金额￥recivedSum元，扣除管理费￥mFee元，实得总额￥msFee元
			TemplateSms sms = new TemplateSms();
			BackstageSet backstageSet = BackstageSet.getCurrentBackstageSet();
			sms.id = Templets.S_RECOVER_ADVANCE_SUCCESS;
			if(sms.status && StringUtils.isNotBlank(userMobile)) {
				String sContent = sms.content.replace("userName", userName);
				sContent = sContent.replace("bidId",backstageSet.loanNumber+bidId);
				sContent = sContent.replace("repayPeriod",this.periods+"");
				sContent = sContent.replace("recivedSum",  DataUtil.formatString(receive)); 
				sContent = sContent.replace("hasP",  DataUtil.formatString(receiveCorpus));
				sContent = sContent.replace("hasI",  DataUtil.formatString(receiveInterest));
				sContent = sContent.replace("bchasY",  "0.00");
				sContent = sContent.replace("hasLFI",  DataUtil.formatString(receiveFees));
				sContent = sContent.replace("mFee",  DataUtil.formatString(investManageFee));                            
				sContent = sContent.replace("msFee",  DataUtil.formatString(Arith.round(receive - investManageFee, 2)));                                                   
				TemplateSms.addSmsTask(userMobile, sContent);
			}
			
			//如果投资用户账户资金没有遭到非法改动，那么就更新其篡改标志，否则不更新
			if(investIsChange){
				investDataTamperproof.updateSignWithLock(investUserId, error);
			}
		}
		
		//改变借款账单的状态为本金垫付还款
		
		String updateBillSql = "update t_bills set status = ?, real_repayment_time = ?, real_repayment_corpus = ?, " + "real_repayment_interest = ? , mer_bill_no = ? where id = ? and status not in(?) ";

		Query updateBill = em.createQuery(updateBillSql).setParameter(1, Constants.ADVANCE_PRINCIIPAL_REPAYMENT).setParameter(2, DateUtil.currentDate()).setParameter(3, payCorpus)
														.setParameter(4, payInterest).setParameter(5, this.merBillNo).setParameter(6, billId).setParameter(7,Constants.ADVANCE_PRINCIIPAL_REPAYMENT);
		try {
			rows = updateBill.executeUpdate();
	    } catch(Exception e) {
			e.printStackTrace();
			Logger.info("修改借款账单的状态时："+e.getMessage());
			error.code = -3;
			error.msg = "数据库异常，导致本金垫付账单失败";
			JPA.setRollbackOnly();
			
			return error.code;
		}
	  
	    if(rows == 0){
			error.code = -1;
			error.msg = "数据库异常，导致本金垫付账单失败";
			JPA.setRollbackOnly();
			
			return error.code;
			
		}
	    
	    /* 本金垫付如果垫付完毕至为：本金垫付还款中(14) */
	    if(this.isEndPayment(bidId) == 0) {
		    sql = "update t_bids set status = ?, last_repay_time = ? where id = ? and status <> ?";
		    Query query = JPA.em().createQuery(sql).setParameter(1, Constants.BID_COMPENSATE_REPAYMENT)
					.setParameter(2, DateUtil.currentDate()).setParameter(3, bidId)
					.setParameter(4, Constants.BID_COMPENSATE_REPAYMENT);
	             
			try {
				rows = query.executeUpdate(); 
			} catch(Exception e) {
				Logger.info(e.getMessage());
				error.code = -1;
				error.msg = "数据库异常";
				JPA.setRollbackOnly();
				
				return error.code;
			}
	    }
		
		//添加管理员操作日志
		DealDetail.supervisorEvent(supervisorId, SupervisorEvent.PRINCIPAL_PAY, "管理员对"+billId+"号 账单本金垫付", error);
		if(error.code < 0) {
			JPA.setRollbackOnly();
			
			return -1;
		}
		
		error.code = 0;
		error.msg = "本金垫付成功";
 		return error.code;
 	}
 	
 	/**
 	 * 后台--我的会员账单--查询催收账单
 	 * @param supervisorId
 	 * @param billIdStr
 	 * @param error
 	 * @return
 	 */
 	public static v_bill_detail_for_collection queryCollection(long supervisorId, long billId, ErrorInfo error) {
 		error.clear();
 		
// 		if(!NumberUtil.isNumericInt(billIdStr)) {
// 			error.code = -1;
//			error.msg = "账单类型有误";
//			
//			return null;
// 		}
 		
 		v_bill_detail_for_collection collection = null;
 		
 		try {
 			collection = v_bill_detail_for_collection.findById(billId);
		} catch (Exception e) {
			e.printStackTrace();
			Logger.info("查询催收账单时："+e.getMessage());
			error.code = -4;
			error.msg = "数据库异常，查询催收账单时失败";
			
			return null;
		}
 		
 		if(collection == null) {
 			collection = new v_bill_detail_for_collection();
 		}
 			
 		return collection;
 	}
 	
 	/**
 	 * 后台--我的会员账单--查询逾期账单
 	 * @param supervisorId
 	 * @param billIdStr
 	 * @param error
 	 * @return
 	 */
 	public static v_bill_detail_for_mark_overdue queryOverdue(long supervisorId, long billId, ErrorInfo error) {
 		error.clear();
 		
 		v_bill_detail_for_mark_overdue overdue = null;
 		
 		try {
 			overdue = v_bill_detail_for_mark_overdue.findById(billId);
		} catch (Exception e) {
			e.printStackTrace();
			Logger.info("查询催收账单时："+e.getMessage());
			error.code = -4;
			error.msg = "数据库异常，查询催收账单时失败";
			
			return null;
		}
 		
 		if(overdue == null) {
 			overdue = new v_bill_detail_for_mark_overdue();
 		}
 			
 		return overdue;
 	}
 	
 	/**
 	 * 账单催收
 	 * @param supervisorId
 	 * @param typeStr 1 站内信 2 邮件 3 电话
 	 * @param billId
 	 * @param error
 	 * @return
 	 */
 	public static int updateBillCollection(long supervisorId, String typeStr, long billId, ErrorInfo error) {
 		error.clear();
 		
 		if(!NumberUtil.isNumericInt(typeStr)) {
 			error.code = -1;
			error.msg = "类型有误";
			
			return error.code;
 		}
 		
 		int type = Integer.parseInt(typeStr);
 		
 		if(type < 0 || type >3) {
 			error.code = -2;
			error.msg = "类型范围有误";
			
			return error.code;
 		}
 		
 		String sql = "";
 		switch (type) {
		case Constants.ONE:
			sql = "update t_bills set notice_count_message = notice_count_message+1 where id = ?";
			break;
			
		case Constants.TWO:
			sql = "update t_bills set notice_count_mail = notice_count_mail+1 where id = ?";
			break;
			
		case Constants.THREE:
			sql = "update t_bills set notice_count_telphone = notice_count_telphone+1 where id = ?";
			break;

		default:
			break;
		}
 		
 		int rows = 0;
 		
 		try {
			rows = JpaHelper.execute(sql, billId).executeUpdate();
		} catch (Exception e) {
			e.printStackTrace();
			Logger.info("修改借款账单的催收次数时："+e.getMessage());
			error.code = -1;
			error.msg = "数据库异常，导致修改借款账单的催收次数失败";
			
			return error.code;
		}
		
		if(rows == 0){
			error.code = -1;
			error.msg = "修改借款账单的催收次数操作没有执行";
			JPA.setRollbackOnly();
			
			return error.code;
			
		}
 		
		//添加管理员操作日志
		if(type == Constants.ONE){
			DealDetail.supervisorEvent(supervisorId, SupervisorEvent.BILL_COLLECTION, "管理员发短信催收"+billId+"号 账单", error);
			if(error.code < 0) {
				JPA.setRollbackOnly();
				
				return -1;
			}
		}
		
		if(type == Constants.TWO){
			DealDetail.supervisorEvent(supervisorId, SupervisorEvent.BILL_COLLECTION, "管理员发邮件催收"+billId+"号 账单", error);
			if(error.code < 0) {
				JPA.setRollbackOnly();
				
				return -1;
			}
		}
	
		DealDetail.supervisorEvent(supervisorId, SupervisorEvent.BILL_COLLECTION, "管理员打电话催收"+billId+"号 账单", error);
		if(error.code < 0) {
			JPA.setRollbackOnly();
			
			return -1;
		}
		
 		error.msg = "催收账单成功！";
 		error.code = 0;
		return error.code;
 	}
 	
 	/**
 	 * 线下收款
 	 * @param info
 	 * @return
 	 */
 	public int offlineCollection(long supervisorId, ErrorInfo error){
 		error.clear();
 		
 		Map<String, Object> billMap = new HashMap<String, Object>();
 		List<Map<String, Object>> investList = new ArrayList<Map<String,Object>>();
 		EntityManager em = JPA.em();
 		int rows = 0;
 		int status;
 		int overdueMark;
 		int creditScore = 0;  //信用积分
 		double investmentRates = Bid.queryRateByBillId(this._id, 1);//理财费率
 		
 		if(investmentRates != 0){
 			investmentRates = investmentRates / 100;
 		}
 		
 		if(this.status != Constants.NO_REPAYMENT && this.status != Constants.ADVANCE_PRINCIIPAL_REPAYMENT){
 			error.code = -1;
			error.msg = "本期账单已还款";
			
			return error.code;
 		}
 		
 		//3.判断还款的借款标有没有债权在转让，如果有，将其状态回归原态,并解冻竞拍者资金
		int resulta = Debt.judgeHasBidTransfer(this.bidId);
		
		if(resulta < 0){
			error.code = -7;
			error.msg = "线下收款出现异常";
			
			return error.code;
		}
		
 		String sql = "select new Map(bill.bid_id as bid_id ,bill.overdue_mark as overdue_mark, bill.repayment_corpus as " +
		"repayment_corpus, bill.repayment_interest as repayment_interest, bill.overdue_fine as overdue_fine," +
		" bill.status as status , bill.repayment_time as repayment_time) from t_bills bill where bill.id = ? and bill.status not in(?,?)";
 		
 		try {
 			billMap = t_bills.find(sql, this._id,Constants.NORMAL_REPAYMENT, Constants.OVERDUE_PATMENT).first();
		} catch (Exception e) {
			e.printStackTrace();
			Logger.info("查询借款账单状态时："+e.getMessage());
			error.code = -1;
			error.msg = "数据库异常，导致查询借款账单状态失败";
			JPA.setRollbackOnly();
			
			return error.code;
		}
 		
 		if(null == billMap){
 			error.code = -1;
			error.msg = "数据库异常，导致查询借款账单状态失败";
			JPA.setRollbackOnly();
			
			return error.code;
 		}
 		
		double repaymentCorpus = (Double) billMap.get("repayment_corpus"); 	//本期的要还款的本金
		double repaymentInterest = (Double) billMap.get("repayment_interest");	//利息
		double repayOverdueFine = (Double)billMap.get("overdue_fine");	//逾期费
		status = (Integer)billMap.get("status");//状态
		overdueMark = (Integer)billMap.get("overdue_mark");//逾期标记
		double LastRate = 0;
		
		String repay_time = billMap.get("repayment_time").toString();
		Date date1 = DateUtil.strToYYMMDDDate(DateUtil.dateToString(new Date())); 
		Date date2 = DateUtil.strToYYMMDDDate(repay_time); 
		boolean flag = date1.before(date2);
		Logger.info("flag===%s",flag);
		if(flag){
			sql = "select ifnull(sum(real_receive_interest),0) + 0.0  from t_bill_invests where bid_id =?";
			double d1 = t_user_details.find(sql,billMap.get("bid_id")).first();
			LastRate = repaymentInterest - d1;
			
		}
		
 		String sql3 = "select new Map(invest.id as id, invest.invest_id as investId, invest.receive_corpus as receive_corpus, invest.receive_interest " +
		"as receive_interest, invest.real_receive_interest as real_receive_interest , invest.user_id as user_id, invest.overdue_fine as overdue_fine) "
		+ "from t_bill_invests as invest where invest.bid_id = ? and invest.periods = ? and invest.status not in(?,?,?)";
 		
 		try {
 			investList = t_bill_invests.find(sql3, this.bidId, this.periods,Constants.NORMAL_RECEIVABLES, 
 					Constants.OVERDUE_RECEIVABLES, Constants.FOR_DEBT_MARK).fetch();
		} catch(Exception e) {
			e.printStackTrace();
			Logger.info("查询投资账单信息时："+e.getMessage());
			error.code = -2;
			error.msg = "数据库异常，导致查询投资账单信息失败";
			JPA.setRollbackOnly();
			
			return error.code;
		}
 		
 		if(null == investList){
 			error.code = -1;
			error.msg = "数据库异常，导致查询借款账单状态失败";
			JPA.setRollbackOnly();
			
			return error.code;
 		}
 		
		for (Map<String, Object> map : investList) {
			long investBillId = (Long) map.get("id");
			long investUserId = (Long) map.get("user_id");
			long investId = (Long) map.get("investId");//投资记录id
			double receiveCorpus = (Double) map.get("receive_corpus");// 本期的投资本金
			double receiveInterest = (Double) map.get("receive_interest");// 本期的投资利息
			double investOverdueFine = (Double) map.get("overdue_fine");// 收取逾期罚款
			double realReceiveInterest = (Double) map.get("real_receive_interest");// 实收利息
			
			double manageFee = BillInvests.getInvestManagerFee(receiveInterest, investmentRates, investUserId);  // 投资管理费

			//查看投资用户是否受到非法资金改动
			DataSafety investDataTamperproof = new DataSafety();
			investDataTamperproof.id = investUserId;
			boolean investIsChange = investDataTamperproof.signCheck(error);
			
			Query query = null;

			// 改变投资账单的收款状态
			String updateSql = "update t_bill_invests set status = ?1, is_offline = ?11, real_receive_time = ?2, real_receive_corpus = real_receive_corpus+?3," +
					" real_receive_interest = ?4, overdue_fine = ?5 where user_id = ?6 and bid_id = ?7 and periods = ?8 and status not in(?9) and invest_id = ?10";
	        
			double receive_rate =0;	     //剩余利息
			if(status == Constants.NO_REPAYMENT && overdueMark == Constants.BILL_NO_OVERDUE) {//正常收款
				
				if(flag){//提前还款   返给理财人剩余利息
					receive_rate =receiveInterest - realReceiveInterest ;
				}
				
				query = em.createQuery(updateSql).setParameter(1, Constants.NORMAL_RECEIVABLES).
		        setParameter(2, DateUtil.currentDate()).setParameter(3, receiveCorpus)
		        .setParameter(4, receive_rate-manageFee).setParameter(5, investOverdueFine).setParameter(6, investUserId)
		        .setParameter(7, this.bidId).setParameter(8, this.periods).setParameter(9, Constants.NORMAL_RECEIVABLES).setParameter(10, investId)
		        .setParameter(11, Constants.ONE);
			}else{
				query = em.createQuery(updateSql).setParameter(1, Constants.OVERDUE_RECEIVABLES).
		        setParameter(2, DateUtil.currentDate()).setParameter(3, receiveCorpus)
		        .setParameter(4, 0 - manageFee).setParameter(5, investOverdueFine).setParameter(6, investUserId)
		        .setParameter(7, this.bidId).setParameter(8, this.periods).setParameter(9, Constants.OVERDUE_RECEIVABLES).setParameter(10, investId)
		        .setParameter(11, Constants.ONE);
			}
			
	        try {
				rows = query.executeUpdate();
			} catch(Exception e) {
				e.printStackTrace();
				Logger.info("更新投资账单数据时："+e.getMessage());
				error.code = -5;
				error.msg = "数据库异常，导致更新投资账单数据失败";
				JPA.setRollbackOnly();
				return error.code;
			}
			
			if(rows == 0){
				error.code = -1;
				error.msg = "更新投资账单数据操作没有执行";
				JPA.setRollbackOnly();
				
				return error.code;
			}
			
			 Map<String, Object> userMap = new HashMap<String,Object>();
				
			String userSql = "select new Map(name as name, email as eamil, mobile as mobile) from t_users where id = ? ";
			
			try {
				userMap = t_users.find(userSql, investUserId).first();
			}catch(Exception e) {
				e.printStackTrace();
				Logger.info("根据用户id查询时："+e.getMessage());
				error.code = -1;
				error.msg = "查询用户名失败";
				
				return error.code;
			}
			double receive = Arith.round(receiveCorpus + receiveInterest + investOverdueFine, 2);
			double totalBalance = Arith.round(Arith.sub(receive, manageFee),2);
			
			String userName = (String)userMap.get("name");
			String userEamil = (String)userMap.get("eamil");
			String userMobile = (String)userMap.get("mobile");
			
			//发送站内信 尊敬的username:\n 您投资的借款title,第repayPeriod期还款已经完成.<br/>paymentModeStr本期应得总额：
			//￥recivedSum,其中本金部分为：hasP元,利息部分：hasI元,实得逾期罚息：hasLFI元<br/>扣除投资管理费：
			//￥mFee元<br/>实得总额：￥msFee元
			TemplateStation station = new TemplateStation();
			station.id = Templets.M_INVEST_RECEIVE;
			
			if(station.status) {
				String mContent = station.content.replace("userName",userName);
				mContent = mContent.replace("title",this.bid.title);
				mContent = mContent.replace("repayPeriod",this.periods+"");
				mContent = mContent.replace("recivedSum",  DataUtil.formatString(receive));
				mContent = mContent.replace("hasP",  DataUtil.formatString(receiveCorpus));
				mContent = mContent.replace("hasI",  DataUtil.formatString(receiveInterest));
				mContent = mContent.replace("bchasY",  DataUtil.formatString(receive_rate));
				mContent = mContent.replace("hasLFI",  DataUtil.formatString(investOverdueFine));
				mContent = mContent.replace("mFee",  DataUtil.formatString(manageFee));
				mContent = mContent.replace("msFee",  DataUtil.formatString(totalBalance));
				
				StationLetter letter = new StationLetter();
				letter.senderSupervisorId = 1;
				letter.receiverUserId = investUserId;
				letter.title = station.title;
				letter.content = mContent;
				 
				letter.sendToUserBySupervisor(error);
			}
			
			//发送邮件 
			TemplateEmail email = new TemplateEmail();
			email.id = Templets.E_INVEST_RECEIVE;
			
			if(email.status) {
				String eContent = email.content.replace("userName", userName);
				eContent = eContent.replace("title",this.bid.title);
				eContent = eContent.replace("repayPeriod",this.periods+"");
				eContent = eContent.replace("recivedSum",  DataUtil.formatString(receive));            
				eContent = eContent.replace("hasP",  DataUtil.formatString(receiveCorpus));            
				eContent = eContent.replace("hasI",  DataUtil.formatString(receiveInterest));
				eContent = eContent.replace("bchasY",  DataUtil.formatString(receive_rate));
				eContent = eContent.replace("hasLFI",  DataUtil.formatString(investOverdueFine));      
				eContent = eContent.replace("mFee",  DataUtil.formatString(manageFee));                
				eContent = eContent.replace("msFee",  DataUtil.formatString(totalBalance));            
				email.addEmailTask(userEamil, email.title, eContent);
			}
			
			//尊敬的userName: 投资的编号bidId借款标repayPeriod期已成功还款，回款金额￥recivedSum元，扣除管理费￥mFee元，实得总额￥msFee元
			TemplateSms sms = new TemplateSms();
			BackstageSet backstageSet = BackstageSet.getCurrentBackstageSet();
			sms.id = Templets.S_RECOVER_ADVANCE_SUCCESS;
			if(sms.status && StringUtils.isNotBlank(userMobile)) {
				String sContent = sms.content.replace("userName", userName);
				sContent = sContent.replace("title",this.bid.title);
				sContent = sContent.replace("bidId",backstageSet.loanNumber+bidId);
				sContent = sContent.replace("repayPeriod",this.periods+"");
				sContent = sContent.replace("recivedSum",  DataUtil.formatString(receive));
				sContent = sContent.replace("hasP",  DataUtil.formatString(receiveCorpus));            
				sContent = sContent.replace("hasI",  DataUtil.formatString(receiveInterest));
				sContent = sContent.replace("bchasY",  DataUtil.formatString(receive_rate));
				sContent = sContent.replace("hasLFI",  DataUtil.formatString(investOverdueFine));   
				sContent = sContent.replace("mFee",  DataUtil.formatString(manageFee));                            
				sContent = sContent.replace("msFee",  DataUtil.formatString(Arith.round(receive - manageFee, 2)));                                                   
				TemplateSms.addSmsTask(userMobile, sContent);
			}
			
			// 返回每个投资用户每期获得的投资本金和剩余利息
			String userBalanceSql2 = "update t_users set balance = balance + ? where id = ?";
			Query userBalance2 = em.createQuery(userBalanceSql2).setParameter(1, receiveCorpus + receive_rate).setParameter(2, investUserId);
			
			 try {
				 rows = userBalance2.executeUpdate();
			 } catch(Exception e) {
					e.printStackTrace();
					Logger.info("修改投资用户的金额时："+e.getMessage());
					error.code = -2;
					error.msg = "数据库异常，导致线下收款失败";
					
					return error.code;
				}
			 
			 if(rows == 0){
					error.code = -1;
					error.msg = "数据库异常，导致线下收款失败";
					JPA.setRollbackOnly();
					
					return error.code;
					
				}
			 
			Map<String, Double> billForDetail = DealDetail.queryUserFund(investUserId, error);
			if(error.code < 0 || billForDetail == null) {
				JPA.setRollbackOnly();
				
				return -1;
			}
			
			double userFreeze = billForDetail.get("freeze");
			double userReceiveAmount = billForDetail.get("receive_amount");
			
			Double investerBalance2 = this.queryBalance(investUserId, error);
			
			DealDetail investDetail = new DealDetail(investUserId, DealType.OFFLINE_COLLECTION, receiveCorpus + receive_rate,
					investBillId, investerBalance2, userFreeze, userReceiveAmount, "第"+this._id+"号账单还款获得金额");
			
			//添加线下收款的交易记录
			investDetail.addDealDetail(error);
			if(error.code < 0) {
				JPA.setRollbackOnly();
				
				return -1;
			}
			
			// 减去每个投资用户每期的管理费
			String userBalanceSql3 = "update t_users set balance = balance - ? where id = ? and balance >= ?";
			Query userBalance3 = em.createQuery(userBalanceSql3).setParameter(1, manageFee).setParameter(2, investUserId).setParameter(3, manageFee);
			
			 try {
				 rows = userBalance3.executeUpdate();
			 } catch(Exception e) {
					e.printStackTrace();
					Logger.info("修改投资用户的金额时："+e.getMessage());
					error.code = -2;
					error.msg = "数据库异常，导致线下收款失败";
					
					return error.code;
				}
			 
			 if(rows == 0){
					error.code = -1;
					error.msg = "数据库异常，导致线下收款失败";
					JPA.setRollbackOnly();
					
					return error.code;
					
				}
			 
			Double investerBalance10 = this.queryBalance(investUserId, error);
			DealDetail investFeeDetail = new DealDetail(investUserId, DealType.CHARGE_INVEST_FEE, manageFee,
					investBillId, investerBalance10, userFreeze, userReceiveAmount, "扣除第"+this._id+"号账单理财管理费");
			
			//添加扣除理财管理费的交易记录
			investFeeDetail.addDealDetail(error);
			if(error.code < 0) {
				JPA.setRollbackOnly();
				
				return -1;
			}
			
			
			//添加平台扣除理财管理费交易记录
			DealDetail.addPlatformDetail(DealType.INVEST_FEE, investBillId, investUserId, -1, DealType.ACCOUNT, manageFee, 1, "平台收取"+this._id+"号 账单投资管理费", error);
			if(error.code < 0) {
				JPA.setRollbackOnly();
				
				return -1;
		    }
			
			if(overdueMark != Constants.BILL_NO_OVERDUE) {
				
				
				// 增加每个投资用户每期获得的罚息
				String userBalanceSql4 = "update t_users set balance = balance + ? where id = ?";
				Query userBalance4 = em.createQuery(userBalanceSql4).setParameter(1, investOverdueFine).setParameter(2, investUserId);
				
				 try {
					 rows = userBalance4.executeUpdate();
				 } catch(Exception e) {
						e.printStackTrace();
						Logger.info("修改投资用户的金额时："+e.getMessage());
						error.code = -2;
						error.msg = "数据库异常，导致线下收款失败";
						
						return error.code;
					}
				 
				 if(rows == 0){
						error.code = -1;
						error.msg = "数据库异常，导致线下收款失败";
						JPA.setRollbackOnly();
						
						return error.code;
						
					}
				 
				Double investerBalance3 = this.queryBalance(investUserId, error);
				DealDetail investOverdueFeeDetail = new DealDetail(investUserId, DealType.ADD_OVERDUE_FEE, investOverdueFine,
						investBillId, investerBalance3, userFreeze, userReceiveAmount, "第"+this._id+"号账单还款获取逾期费");
				
				//添加获取逾期费用的交易记录
				investOverdueFeeDetail.addDealDetail(error);
				if(error.code < 0) {
					JPA.setRollbackOnly();
					
					return -1;
				  }
				}
			
			//如果投资用户账户资金没有遭到非法改动，那么就更新其篡改标志，否则不更新
			if(investIsChange){
				investDataTamperproof.updateSignWithLock(investUserId, error);
			}
		}
	
		//借款账单没有逾期
		if(status == Constants.NO_REPAYMENT && overdueMark == Constants.BILL_NO_OVERDUE){
			
			String updateBillSql = "update t_bills set status = ?, real_repayment_time = ?, real_repayment_corpus = ?, real_repayment_interest = ? where id = ? and status not in(?)";
			
			Query updateBill = em.createQuery(updateBillSql).setParameter(1, Constants.NORMAL_REPAYMENT).setParameter(2, DateUtil.currentDate())
	        .setParameter(3, repaymentCorpus).setParameter(4, repaymentInterest).setParameter(5, this._id).setParameter(6, Constants.NORMAL_REPAYMENT);
			
			try {
				rows = updateBill.executeUpdate();
			} catch(Exception e) {
				e.printStackTrace();
				Logger.info("修改借款账单数据时："+e.getMessage());
				error.code = -3;
				error.msg = "数据库异常，导致线下收款失败";
				JPA.setRollbackOnly();
				
				return error.code;
			}
			
			if(rows == 0){
				error.code = -1;
				error.msg = "数据库异常，导致线下收款失败";
				JPA.setRollbackOnly();
				
				return error.code;
			}
			
			//正常还款添加信用积分
			creditScore = DealDetail.addCreditScore(this.bid.userId, 2, 0, this._id, "第"+this._id+"号账单正常还款添加信用积分", error);
			if(error.code < 0) {
				JPA.setRollbackOnly();
				
				return -1;
			 }
			
		}else{
			//逾期的借款账单
            String updateBillSql = "update t_bills set status = ?, real_repayment_time = ?, real_repayment_corpus = ?, real_repayment_interest = ?," +
            		" overdue_fine = ? where id = ? and status not in(?)";
			
			Query updateBill = em.createQuery(updateBillSql).setParameter(1, Constants.OVERDUE_PATMENT).setParameter(2, DateUtil.currentDate())
	        .setParameter(3, repaymentCorpus).setParameter(4, repaymentInterest).setParameter(5, repayOverdueFine)
	        .setParameter(6, this._id).setParameter(7, Constants.OVERDUE_PATMENT);
			
			try {
				rows = updateBill.executeUpdate();
			} catch(Exception e) {
				e.printStackTrace();
				Logger.info("修改借款账单数据时："+e.getMessage());
				error.code = -3;
				error.msg = "数据库异常，导致线下收款失败";
				JPA.setRollbackOnly();
				
				return error.code;
			}
			
			if(rows == 0){
				error.code = -1;
				error.msg = "数据库异常，导致线下收款失败";
				JPA.setRollbackOnly();
				
				return error.code;
				
			}
			
		//逾期还款减去信用积分
	    creditScore = DealDetail.addCreditScore(this.bid.userId, -1, 0, this._id, "第"+this._id+"号账单逾期还款扣除信用积分", error);
		if(error.code < 0) {
			JPA.setRollbackOnly();
			
			return -1;
		 }
	   }
		
		//更新用户的信用等级
		/* 
		 * v.7.2.7 不及时更新用户信用等级，每天凌晨统一更新
		User.updateCreditLevel(this.bid.userId, error);
		if (error.code < 0) {
			JPA.setRollbackOnly();
			
			return -1;
		}
		*/
		
		//添加管理员操作日志
		DealDetail.supervisorEvent(supervisorId, SupervisorEvent.OFFLINE_COLLECTION, "第"+this._id+"号账单管理员执行线下收款操作 ", error);
		if(error.code < 0) {
			JPA.setRollbackOnly();
			
			return -1;
		}
		
        Map<String, Object> userMap = new HashMap<String,Object>();
		
		String userSql = "select new Map(name as name, email as eamil, mobile as mobile) from t_users where id = ? ";
		
		try {
			userMap = t_users.find(userSql, this.bid.userId).first();
		}catch(Exception e) {
			e.printStackTrace();
			Logger.info("根据用户id查询时："+e.getMessage());
			error.code = -1;
			error.msg = "查询用户名失败";
			
			return error.code;
		}
		
		String userName = (String)userMap.get("name");
		String userEamil = (String)userMap.get("eamil");
		String userMobile = (String)userMap.get("mobile");
		
		//发送站内信  尊敬的username:\n [date] 您在晓风安全网贷系统6对标的【title】还款了￥needSum 元
		TemplateStation station = new TemplateStation();
		station.id = Templets.M_SUCCESS_PAY;
		BackstageSet backstageSet = BackstageSet.getCurrentBackstageSet();
		if(station.status) {
			String mContent = station.content.replace("userName", userName);
			mContent = mContent.replace("date",DateUtil.dateToString(this.bid.time));
			mContent = mContent.replace("bidId",backstageSet.loanNumber+bidId);
			mContent = mContent.replace("repayPeriod",this.periods+"");
			mContent = mContent.replace("needSum",DataUtil.formatString(repaymentCorpus +  repaymentInterest + repayOverdueFine));
			mContent = mContent.replace("rcp",DataUtil.formatString(repaymentCorpus));
			mContent = mContent.replace("hasI",DataUtil.formatString(repaymentInterest));
			mContent = mContent.replace("rodf",DataUtil.formatString(repayOverdueFine));
			mContent = mContent.replace("creditScore",-creditScore+"");
			
			StationLetter letter = new StationLetter();
			letter.senderSupervisorId = 1;
			letter.receiverUserId = this.bid.userId;
			letter.title = station.title;
			letter.content = mContent;
			 
			letter.sendToUserBySupervisor(error);
		}
		
		//发送邮件 尊敬的username:\n [date] 您在晓风安全网贷系统6对标的【title】还款了￥needSum 元
		TemplateEmail email = new TemplateEmail();
		email.id = Templets.E_SUCCESS_PAY;
		
		if(email.status) {
			String eContent = email.content.replace("userName", userName);
			eContent = eContent.replace("date",this.bid.time+"");
			eContent = eContent.replace("title",this.bid.title);
			eContent = eContent.replace("needSum",(repaymentCorpus +  LastRate + repayOverdueFine)+"");
			eContent = eContent.replace("creditScore",-creditScore+"");
			email.addEmailTask(userEamil, email.title, eContent);
		}
		
		//发送短信  尊敬的userName: 你申请的编号bidId借款标repayPeriod期成功还款，还款金额￥needSum元，
		TemplateSms sms = new TemplateSms();
	
		sms.id = Templets.S_REPAY_SUCCESS;
		if(sms.status && StringUtils.isNotBlank(userMobile)) {
			String sContent = sms.content.replace("userName", userName);
			sContent = sContent.replace("bidId",backstageSet.loanNumber+bidId);
			sContent = sContent.replace("repayPeriod",this.periods+"");
			sContent = sContent.replace("needSum",DataUtil.formatString(repaymentCorpus +  repaymentInterest + repayOverdueFine));
			sContent = sContent.replace("rcp",DataUtil.formatString(repaymentCorpus));
			sContent = sContent.replace("hasI",DataUtil.formatString(repaymentInterest));
			sContent = sContent.replace("rodf",DataUtil.formatString(repayOverdueFine));
			TemplateSms.addSmsTask(userMobile, sContent);
		}
		
		//11.判断这个借款标是否已还完款，若还完标记这个借款标为已还款完状态
		if(this.isEndPayment(bidId, error) == 0){
			String bidSql = "update t_bids set status = ?, last_repay_time = ? where id = ?";
		
            Query query = em.createQuery(bidSql).setParameter(1, Constants.BID_REPAYMENTS).setParameter(2, DateUtil.currentDate())
            .setParameter(3, bidId);
            
			try {
				rows = query.executeUpdate();
			
			} catch(Exception e) {
				e.printStackTrace();
				Logger.info("修改借款标为已还款完状态时："+e.getMessage());
				error.code = -7;
				error.msg = "还款出现异常，导致线下收款失败";
				JPA.setRollbackOnly();
				
				return error.code;
			}
			
			if(rows == 0){
				error.code = -1;
				error.msg = "还款出现异常，导致线下收款失败";
				JPA.setRollbackOnly();
				
				return error.code;
			}
		}
		
		//2. 平台保障金资金减少（线下收款）
		double payment = this.repaymentCorpus + LastRate + this.overdueFine;// 总共要还款的金额
		DealDetail.addPlatformDetail(DealType.OFFLINE_PAY_FEE, this._id, -1, this.bid.userId, DealType.ACCOUNT, payment, 2, "平台支出第"+this._id+"号账单普通线下收款操作金额", error);
		
		if(error.code < 0) {
			error.code = -1;
			error.msg = "添加线下收款交易记录出现异常，导致线下收款失败";
			JPA.setRollbackOnly();
			
			return error.code;
		}
		
		error.msg = "线下收款操作成功";
		error.code = 0;
		return error.code;
 	}
 	
 	/**
 	 * 债权转让成功后投资账单的转换
 	 * @param bidId
 	 * @param originalInvestUserId
 	 * @param presentInvestUserId
 	 * @param info -1 失败 0成功
 	 * @return
 	 */
 	public static int debtTransfer(String paymentMerBillNo, long bidId, long originalInvestUserId, long presentInvestUserId,long originalInvestId,long presentInvestId, ErrorInfo error){
 		error.clear();
 		EntityManager em = JPA.em();
 		
 		String sql = "update t_bill_invests set user_id =? ,invest_id = ? where bid_id = ? and user_id = ? and invest_id = ? and status not in (?,?,?,?)";
 	    Query query = em.createQuery(sql).setParameter(1, presentInvestUserId).setParameter(2, presentInvestId).
 	    setParameter(3, bidId).setParameter(4, originalInvestUserId).setParameter(5, originalInvestId).setParameter(6, Constants.NORMAL_RECEIVABLES)
 	    .setParameter(7, Constants.ADVANCE_PRINCIIPAL_RECEIVABLES).setParameter(8, Constants.OVERDUE_RECEIVABLES)
 	    .setParameter(9, Constants.FOR_DEBT_MARK);
 		
 		if(Constants.IPS_ENABLE) {
 			sql = "update t_bill_invests set user_id =? ,invest_id = ?, mer_bill_no = ? where bid_id = ? and user_id = ? and invest_id = ? and status not in (?,?,?,?)";
 			query = em.createQuery(sql).setParameter(1, presentInvestUserId).setParameter(2, presentInvestId).setParameter(3, paymentMerBillNo)
 			 	    .setParameter(4, bidId).setParameter(5, originalInvestUserId).setParameter(6, originalInvestId).setParameter(7, Constants.NORMAL_RECEIVABLES)
 			 	    .setParameter(8, Constants.ADVANCE_PRINCIIPAL_RECEIVABLES).setParameter(9, Constants.OVERDUE_RECEIVABLES)
 			 	    .setParameter(10, Constants.FOR_DEBT_MARK);
 		}
 	    
 	    int rows = 0;
 	    
 	   try {
 		  rows = query.executeUpdate();
		} catch(Exception e) {
			e.printStackTrace();
			Logger.info("更新投资账单id时："+e.getMessage());
			error.code = -1;
			error.msg = "债权转让成功后，数据库异常导致投资账单转换为新债权人失败";
			JPA.setRollbackOnly();
			
			return error.code;
		}
		
		if(rows == 0){
			error.code = -1;
			error.msg = "更新投资账单数据操作没有执行";
			JPA.setRollbackOnly();
			
			return error.code;
			
		}
		
 		return 0;
 	}
 	
 	/**
 	 * 在投资账单里复制符合条件的记录
 	 * @param bidId
 	 * @param userId
 	 * @param error
 	 * @return
 	 */
 	public static int investBillsTransfer(long bidId, long userId,long investId,ErrorInfo error){
 		error.clear();
 		
 		String sql = "INSERT INTO t_bill_invests(user_id,invest_id, bid_id,mer_bill_no,periods,title,receive_time,receive_corpus," +
 				"receive_interest,status,overdue_fine,real_receive_time,real_receive_corpus,real_receive_interest" +
 				") SELECT user_id,invest_id, bid_id,mer_bill_no,periods,title,receive_time,receive_corpus,receive_interest,-7," +
 				"overdue_fine,real_receive_time,real_receive_corpus,real_receive_interest FROM t_bill_invests " +
 				"WHERE user_id = ? AND bid_id = ? AND invest_id = ? and status not in (?,?,?)";
 		
 		try {
 			JPA.em().createNativeQuery(sql).setParameter(1, userId).setParameter(2, bidId).setParameter(3, investId).setParameter(4, Constants.NORMAL_RECEIVABLES).
 			setParameter(5, Constants.ADVANCE_PRINCIIPAL_RECEIVABLES).setParameter(6, Constants.OVERDUE_RECEIVABLES).executeUpdate();
		} catch(Exception e) {
			e.printStackTrace();
			Logger.info("更新投资账单id时："+e.getMessage());
			error.code = -1;
			error.msg = "债权转让成功后，数据库异常导致投资账单转换为新债权人失败";
			JPA.setRollbackOnly();
			
			return error.code;
		}
		
		return 0;
 	}
 	
 	/**
 	 * 每天对逾期的借款账单重新计算逾期罚款并对其投资账单做对应的的修改
 	 * @param info
 	 * @throws ParseException
 	 */
 	public int upadateOverdueFee(ErrorInfo error){
		  error.clear();
		  EntityManager em = JPA.em();
		
		   //1.修改借款账单逾期生成的数据
		  String updateBillSql = "update t_bills t1,(select c.id, round((c.days * c.amount * c.overdue_rate/100),2)" +
	  		" overdue_fee from (select a.id, b.overdue_rate, DATEDIFF(now(), a.repayment_time) days, " +
	  		"(a.repayment_corpus + a.repayment_interest) amount from t_bills a join t_bids b ON a.bid_id = b.id " +
	  		"where a.`status` in(-1,-2) and a.overdue_mark in(-1,-2,-3) group by a.id) c) t2 set t1.overdue_fine =" +
	  		" t2.overdue_fee where t1.id = t2.id";
		  
		  Query updateBill = em.createNativeQuery(updateBillSql);
		    
		  try {
			  updateBill.executeUpdate();
		  } catch(Exception e) {
				e.printStackTrace();
				Logger.info("修改借款账单逾期生成的数据时："+e.getMessage());
				error.code = -3;
				error.msg = "数据库异常，导致修改借款账单逾期生成的数据失败";
				JPA.setRollbackOnly();
					
				return error.code;
			}
		  
		   //2.修改理财账单逾期生成的数据
		  String updateInvestBillSql = "update t_bill_invests t1,(select t3.id, round((t4.overdue_fine * ifnull(t6.amount,0) / t5.amount), 2)" +
		  		" check_fine, t3.periods, t3.bid_id from (select b.bid_id, b.periods, b.overdue_fine from t_bills b where " +
		  		"b.overdue_mark in(-1,-2,-3) and b.status in(-1,-2)) t4 left join (select a.invest_id, a.id, a.periods, a.bid_id, " +
		  		"a.user_id from t_bill_invests a) t3 on t3.bid_id = t4.bid_id and t3.periods = t4.periods left join " +
		  		"(select c.id, c.amount from t_bids c) t5 on t3.bid_id = t5.id left join (select d.id, d.bid_id, d.user_id, " +
		  		"d.amount from t_invests d) t6 on t3.bid_id = t6.bid_id and t3.user_id = t6.user_id and t3.invest_id = t6.id) t2 set " +
		  		"t1.overdue_fine = check_fine where t1.id = t2.id";
		  
		  Query updateInvestBill = em.createNativeQuery(updateInvestBillSql);
		    
		  try {
			  updateInvestBill.executeUpdate();
		  } catch(Exception e) {
				e.printStackTrace();
				Logger.info("修改理财账单逾期生成的数据时："+e.getMessage());
				error.code = -3;
				error.msg = "数据库异常，导致修改理财账单逾期生成的数据失败";
				JPA.setRollbackOnly();
					
				return error.code;
			}
		
		  error.code = 0;
		  return error.code;
 	}
 	
 	/**
 	 * 查询某个标的所有账单
 	 * @param pageBean 分页对象
 	 * @param userId 用户ID
 	 * @param bidId 标ID
 	 * @param error 信息值
 	 * @return List<v_bill_loan>
 	 */
	public static List<v_bill_loan> queryMyLoanBills(
			PageBean<v_bill_loan> pageBean, long userId, long bidId,
			ErrorInfo error) {
		error.clear();
		
 		int count = -1;
 		String condition = null;
 		
 		if(userId > 0)
 			condition = "id = " + userId;
 		else if(bidId > 0)
 			condition = "bid_id = " + bidId;
 		else
 			condition = "id = " + userId + "bid_id = " + bidId;
 		
 		try {
 			count = (int)v_bill_loan.count(condition);
		} catch (Exception e) {
			Logger.error("账单->查询我的借款账单,查询总行数:" + e.getMessage());
			error.msg = "加载我的借款账户失败!";
			
			return null;
		}
        
		if(count < 1)
			return new ArrayList<v_bill_loan>();
        
		pageBean.totalCount = count;
			
		try {
			return v_bill_loan.find(condition).fetch(pageBean.currPage, pageBean.pageSize);
		} catch (Exception e) {
			Logger.error("账单->查询我的借款账单:" + e.getMessage());
			error.msg = "加载我的借款账户失败!";
			
			return null;
		}	
 	} 
	
	/**
	 * 查询我所有的借款账单
	 * @param error
	 * @return
	 */
	public static List<v_bill_loan> queryMyAllLoanBills(ErrorInfo error) {
		error.clear();
		
		List<v_bill_loan> bills = null;
		
		try {
 			bills = v_bill_loan.find("user_id = ?", User.currUser().id).fetch();
		} catch (Exception e) {
			e.printStackTrace();
			Logger.error("查询我所有的借款账单:" + e.getMessage());
			error.code = -1;
			error.msg = "查询我所有的借款账单失败!";
			
			return null;
		}
		
		return bills;
	}
 	
 	/**
 	 * 查询用户的所有借款账单
 	 * @param pageBean 分页对象
 	 * @param error 信息值
 	 * @return List<v_bill_loan>
 	 */
	public static PageBean<v_bill_loan> queryMyLoanBills(long userId, int payType, int isOverType,
			int keyType, String keyStr, int currPageStr, int pageSizeNum,  ErrorInfo error) {
		error.clear();
		
 		int count = 0;
 		int currPage = Constants.ONE;
 		int pageSize = Constants.FIVE;
 		
 		if(pageSizeNum != 0){
 			pageSize = pageSizeNum;
 		}
 		
        Map<String,Object> conditionMap = new HashMap<String, Object>();
 		List<v_bill_loan> bills = new ArrayList<v_bill_loan>();
 		List<Object> values = new ArrayList<Object>();
 		StringBuffer conditions = new StringBuffer("1=1 ");
 		
 		if((payType < 0) || (payType > 2)){
 			payType = 0;
 		}
 		
 		if((isOverType < 0) || (isOverType > 2)){
 			isOverType = 0;
 		}
 		
 		if((keyType < 0) || (keyType > 3)){
 			keyType = 0;
 		}
 		
 		if(currPageStr != 0){
 			currPage = currPageStr;
 		}
 		
 		if(StringUtils.isNotBlank(keyStr)) {
			conditions.append(Constants.LOAN_BILL_ALL[keyType]);
			values.add("%"+keyStr.trim()+"%");
		}
 		
 		conditions.append(Constants.LOAN_BILL_REPAYMENT[payType]);
 		conditions.append(Constants.LOAN_BILL_OVDUE[isOverType]);
 		conditions.append("and user_id = "+ userId);
 		
 		conditionMap.put("payType", payType);
		conditionMap.put("isOverType", isOverType);
		conditionMap.put("keyType", keyType);
		conditionMap.put("key", keyStr);
 		
 		try {
 			count =  (int) v_bill_loan.count(conditions.toString(), values.toArray());
 			bills = v_bill_loan.find(conditions.toString() + " order by bid_id desc,period", values.toArray()).fetch(currPage, pageSize);
		} catch (Exception e) {
			e.printStackTrace();
			Logger.error("查询我的借款账单:" + e.getMessage());
			error.msg = "加载我的借款账单失败!";
			
			return null;
		}
        
		PageBean<v_bill_loan> page = new PageBean<v_bill_loan>();
		page.pageSize = pageSize;
		page.currPage = currPage;
		page.totalCount = count;
		page.conditions = conditionMap;
		
		page.page = bills;
		
		return page;
 	} 
 	
 	/**
 	 * 我的账单详情
 	 * @param id
 	 * @param currPage
 	 * @param info
 	 * @return
 	 */
 	public static v_bill_detail queryBillDetails(long id, long userId, ErrorInfo error){
 		error.clear();
		
 		v_bill_detail billDetail = new v_bill_detail();
 		try {
 			billDetail = v_bill_detail.find("id = ? and user_id = ?", id,userId).first();
		}catch (Exception e) {
			e.printStackTrace();
			Logger.info("查询我的借款账单详情时："+e.getMessage());
			error.code = -1;
			error.msg = "由于数据库异常，查询我的借款账单详情失败";
			
			return null;
		}
		
		if(null == billDetail){
			error.code = -1;
			error.msg = "由于数据库异常，查询我的借款账单详情失败";
			
			return null;
		}
		
 		return billDetail;
 	}
 	
 	/**
 	 * 我的账单详情
 	 * @param id
 	 * @param currPage
 	 * @param info
 	 * @return
 	 */
 	public static v_bill_detail queryBillDetails(long id, ErrorInfo error){
 		error.clear();
		
 		v_bill_detail billDetail = new v_bill_detail();
 		try {
 			billDetail = v_bill_detail.findById(id);
		}catch (Exception e) {
			e.printStackTrace();
			Logger.info("查询我的借款账单详情时："+e.getMessage());
			error.code = -1;
			error.msg = "由于数据库异常，查询我的借款账单详情失败";
			
			return null;
		}
		
		if(null == billDetail){
			error.code = -1;
			error.msg = "由于数据库异常，查询我的借款账单详情失败";
			
			return null;
		}
		
 		return billDetail;
 	}
 	
 	/**
 	 * 我的借款账单——-历史还款情况
 	 * @param id
 	 * @param currPage
 	 * @param info
 	 * @return
 	 */
 	public static PageBean<v_bill_repayment_record> queryBillReceivables(long bidid, int currPage, int pageSize, ErrorInfo error){
 		error.clear();
 		
		List<v_bill_repayment_record> bills = new ArrayList<v_bill_repayment_record>();
		PageBean<v_bill_repayment_record> page = new PageBean<v_bill_repayment_record>();
		page.pageSize = Constants.TWO;
		page.currPage = Constants.ONE;
		
		if(currPage != 0){
			page.currPage = currPage;
		}
		
		if(pageSize != 0){
			page.pageSize = pageSize;
		}
		
		String sql="select new v_bill_repayment_record( a.current_pay_amount as current_pay_amount, a.real_current_pay_amount as real_current_pay_amount, a.title as title," +
				"a.overdue_mark as overdue_mark, a.status as status, " +
				"a.real_repayment_time as real_repayment_time, a.repayment_time as " +
				"repayment_time )from v_bill_repayment_record as a where a.bid_id = ?";
		try {
			page.totalCount = (int)v_bill_repayment_record.count("bid_id = ?", bidid);
			bills = v_bill_repayment_record.find(sql,bidid).fetch(page.currPage, page.pageSize);
		}catch (Exception e) {
			e.printStackTrace();
			Logger.info("查询我的借款账单收款情况时："+e.getMessage());
			error.code = -1;
			error.msg = "由于数据库异常，查询我的借款账单收款情况失败";
			
			return null;
		}
		
		page.page = bills;

		return page;
 	}
 	
 	/**
 	 * 我的信用等级——-正常还款积分明细
 	 * @param id
 	 * @param currPage
 	 * @param info
 	 * @return
 	 */
 	public static PageBean<v_user_detail_credit_score_normal_repayment> queryBillNormalPayment(long userId, int currPage, ErrorInfo error){
 		error.clear();
 		
		List<v_user_detail_credit_score_normal_repayment> bills = new ArrayList<v_user_detail_credit_score_normal_repayment>();
		PageBean<v_user_detail_credit_score_normal_repayment> page = new PageBean<v_user_detail_credit_score_normal_repayment>();
		page.pageSize = 2;
		page.currPage = currPage;
		
		StringBuffer sql = new StringBuffer("");
		sql.append(SQLTempletes.PAGE_SELECT);
		sql.append(SQLTempletes.V_USER_DETAIL_CREDIT_SCORE_NORMAL_REPAYMENT);
		sql.append(" and score.id = ?");
		
		List<Object> params = new ArrayList<Object>();
		params.add(userId);
		
		try {
			EntityManager em = JPA.em();
            Query query = em.createNativeQuery(sql.toString(),v_user_detail_credit_score_normal_repayment.class);
            for(int n = 1; n <= params.size(); n++){
                query.setParameter(n, params.get(n-1));
            }
            query.setFirstResult((currPage - 1) * page.pageSize);
            query.setMaxResults(page.pageSize);
            bills = query.getResultList();
            
            page.totalCount = QueryUtil.getQueryCountByCondition(em, sql.toString(), params);
            
		}catch (Exception e) {
			e.printStackTrace();
			Logger.info("查询我的正常还款积分明细时："+e.getMessage());
			error.code = -1;
			error.msg = "由于数据库异常，查询正常还款积分明细失败";
			
			return null;
		}
		
		page.page = bills;

		return page;
 	}
 	
 	/**
 	 * 我的信用等级——-逾期账单积分明细
 	 * @param id
 	 * @param currPage
 	 * @param info
 	 * @return
 	 */
 	public static PageBean<v_user_detail_credit_score_overdue> queryBillOverdueMark(long userId, int currPage, ErrorInfo error){
 		error.clear();
 		
		List<v_user_detail_credit_score_overdue> bills = new ArrayList<v_user_detail_credit_score_overdue>();
		PageBean<v_user_detail_credit_score_overdue> page = new PageBean<v_user_detail_credit_score_overdue>();
		page.pageSize = 2;
		page.currPage = currPage;
		
		StringBuffer sql = new StringBuffer("");
		sql.append(SQLTempletes.PAGE_SELECT);
		sql.append(SQLTempletes.V_USER_DETAIL_CREDIT_SCORE_OVERDUE);
		sql.append(" and id = ?");
		
		List<Object> params = new ArrayList<Object>();
		params.add(userId);
		
		try {
			EntityManager em = JPA.em();
            Query query = em.createNativeQuery(sql.toString(),v_user_detail_credit_score_overdue.class);
            for(int n = 1; n <= params.size(); n++){
                query.setParameter(n, params.get(n-1));
            }
            query.setFirstResult((currPage - 1) * page.pageSize);
            query.setMaxResults(page.pageSize);
            bills = query.getResultList();
            
            page.totalCount = QueryUtil.getQueryCountByCondition(em, sql.toString(), params);
            
		}catch (Exception e) {
			e.printStackTrace();
			Logger.info("查询我的逾期账单积分明细时："+e.getMessage());
			error.code = -1;
			error.msg = "由于数据库异常，查询逾期账单积分明细失败";
			
			return null;
		}
		
		page.page = bills;

		return page;
 	}
 	
 	/**
 	 * 我的会员账单--本月到期账单
 	 * @param supervisorId
 	 * @param yearStr
 	 * @param monthStr
 	 * @param typeStr
 	 * @param key
 	 * @param currPageStr
 	 * @param pageSizeStr
 	 * @param error
 	 * @return
 	 */
 	public static PageBean<v_bill_month_maturity> queryBillMonthMaturity(long supervisorId, String yearStr, String monthStr, 
 			String typeStr, String key, String orderTypeStr, String currPageStr, String pageSizeStr, ErrorInfo error){
 		error.clear();
 		
 		int year = 0;
 		int month = 0;
 		int type = 0;
 		int orderType = 0;
 		int currPage = Constants.ONE;
 		int pageSize = Constants.TEN;
 		
 		if(NumberUtil.isNumericInt(yearStr)) {
 			year = Integer.parseInt(yearStr);
 		}
 		
 		if(NumberUtil.isNumericInt(monthStr)) {
 			month = Integer.parseInt(monthStr);
 		}
 		
 		if(NumberUtil.isNumericInt(typeStr)) {
 			type = Integer.parseInt(typeStr);
 		}
 		
 		if(NumberUtil.isNumericInt(orderTypeStr)) {
 			orderType = Integer.parseInt(orderTypeStr);
 		}
 		
 		if(NumberUtil.isNumericInt(currPageStr)) {
 			currPage = Integer.parseInt(currPageStr);
 		}
 		
 		if(NumberUtil.isNumericInt(pageSizeStr)) {
 			pageSize = Integer.parseInt(pageSizeStr);
 		}
 		
// 		if(year < 0 || year > 5) {
// 			year = 0;
// 		}
// 		
 		if(month < 0 || month > 12) {
 			month = 0;
 		}
 		
 		if(type < 0 || type > 3) {
 			type = 0;
 		}
 		
 		if(orderType < 0 || orderType > 10) {
 			orderType = 0;
 		}
 		
 		Map<String,Object> conditionMap = new HashMap<String, Object>();
		
		conditionMap.put("year", year);
		conditionMap.put("month", month);
		conditionMap.put("type", type);
		conditionMap.put("key", key);
		conditionMap.put("orderType", orderType);
 		
		StringBuffer conditions = new StringBuffer("1=1 ");
		List<Object> values = new ArrayList<Object>();
		
		if(year != 0 ) {
			conditions.append("and year = ? ");
			values.add(year);
//			values.add(Constants.TIME_YEAR[year]);
		}
		
		if(month != 0) {
			conditions.append("and month = ? ");
			values.add(month);
//			values.add(Constants.TIME_MONTH[month]);
		}
		
		if(StringUtils.isNotBlank(key)) {
			conditions.append(Constants.C_TYPE[type]);
			
			if(type == 0 ) {
				values.add("%"+key+"%");
				values.add("%"+key+"%");
				values.add("%"+key+"%");
			}else {
				values.add("%"+key+"%");
			}	
		}
		
		conditions.append("and supervisor_id = ? ");
		values.add(supervisorId);
		
		if(orderType != 0) {
			conditions.append("order by "+Constants.BILL_ORDER_Maturity[orderType]);
		}else{
			conditions.append("order by bid_id desc");
		}
		
		List<v_bill_month_maturity> bills = new ArrayList<v_bill_month_maturity>();
		int count = 0;
		
		try {
			count = (int)v_bill_month_maturity.count(conditions.toString(), values.toArray());
			bills = v_bill_month_maturity.find(conditions.toString(), values.toArray()).fetch(currPage, pageSize);
		}catch (Exception e) {
			e.printStackTrace();
			Logger.info("查询本月到期账单情况时："+e.getMessage());
			error.code = -3;
 			error.msg = "";
			
			return null;
		}
		
		PageBean<v_bill_month_maturity> page = new PageBean<v_bill_month_maturity>();
		
		page.pageSize = pageSize;
		page.currPage = currPage;
		page.page = bills;
		page.totalCount = count;
		page.conditions = conditionMap;

		return page;
 	}
 	
 	/**
 	 * 部门账单管理--本月到期账单
 	 * @param id
 	 * @param currPage
 	 * @param info
 	 * @return
 	 */
 	public static PageBean<v_bill_department_month_maturity> queryBillDepartmentMonthMaturity(int noPage, long supervisorId, String yearStr, String monthStr, 
 			String typeStr, String key, String kefuStr, String orderTypeStr, String currPageStr, String pageSizeStr, ErrorInfo error){
 		error.clear();
 		
 		int year = 0;
 		int month = 0;
 		int type = 0;
 		int kefu = 0;
 		int orderType = 0;
 		int currPage = Constants.ONE;
 		int pageSize = Constants.TEN;
 		
 		if(NumberUtil.isNumericInt(yearStr)) {
 			year = Integer.parseInt(yearStr);
 		}
 		
 		if(NumberUtil.isNumericInt(monthStr)) {
 			month = Integer.parseInt(monthStr);
 		}
 		
 		if(NumberUtil.isNumericInt(typeStr)) {
 			type = Integer.parseInt(typeStr);
 		}
 		
 		if(NumberUtil.isNumericInt(kefuStr)) {
 			kefu = Integer.parseInt(kefuStr);
 		}
 		
 		if(NumberUtil.isNumericInt(orderTypeStr)) {
 			orderType = Integer.parseInt(orderTypeStr);
 		}
 		
 		if(NumberUtil.isNumericInt(currPageStr)) {
 			currPage = Integer.parseInt(currPageStr);
 		}
 		
 		if(NumberUtil.isNumericInt(pageSizeStr)) {
 			pageSize = Integer.parseInt(pageSizeStr);
 		}
 		
// 		if(year < 0 || year > 5) {
// 			year = 0;
// 		}
// 		
 		if(month < 0 || month > 12) {
 			month = 0;
 		}
 		
 		if(type < 0 || type > 3) {
 			type = 0;
 		}
 		
 		if(orderType < 0 || orderType > 10) {
 			orderType = 0;
 		}
		
 		Map<String,Object> conditionMap = new HashMap<String, Object>();
		
		conditionMap.put("year", year);
		conditionMap.put("month", month);
		conditionMap.put("type", type);
		conditionMap.put("key", key);
		conditionMap.put("kefu", kefu);
		conditionMap.put("orderType", orderType);
 		
		StringBuffer conditions = new StringBuffer("1=1 ");
		List<Object> values = new ArrayList<Object>();
		
		if(year != 0 ) {
			conditions.append("and year = ? ");
			values.add(year);
//			values.add(Constants.TIME_YEAR[year]);
		}
		
		if(month != 0) {
			conditions.append("and month = ? ");
			values.add(month);
//			values.add(Constants.TIME_MONTH[month]);
		}
		
		if(StringUtils.isNotBlank(key)) {
			conditions.append(Constants.C_TYPE[type]);
			
			if(type == 0 ) {
				values.add("%"+key+"%");
				values.add("%"+key+"%");
				values.add("%"+key+"%");
			}else {
				values.add("%"+key+"%");
			}
		}
		
		if(kefu != 0) {
			conditions.append("and supervisor_id = ? ");
			values.add(kefu);
		}
		
		if(orderType != 0) {
			conditions.append("order by "+Constants.BILL_ORDER_Maturity[orderType]);
		}else{
//			conditions.append("order by bid_id desc"); 
			conditions.append("order by repayment_time");  //默认排序修改为：按还款时间升序排列
		}
		
		List<v_bill_department_month_maturity> bills = new ArrayList<v_bill_department_month_maturity>();
		int count = 0;
 		
		try {
			count = (int)v_bill_department_month_maturity.count(conditions.toString(), values.toArray());
		
			if(noPage != Constants.NO_PAGE){
				bills = v_bill_department_month_maturity.find(conditions.toString(), values.toArray()).fetch(currPage, pageSize);
			}else{
				bills = v_bill_department_month_maturity.find(conditions.toString(), values.toArray()).fetch();			
			}
			
		}catch (Exception e) {
			e.printStackTrace();
			Logger.info("查询部门账单管理--本月到期账单情况时："+e.getMessage());
			error.code = -1;
			error.msg = "由于数据库异常，查询部门账单管理--本月到期账单情况失败";
			
			return null;
		}
		
		PageBean<v_bill_department_month_maturity> page = new PageBean<v_bill_department_month_maturity>();
		
		page.pageSize = pageSize;
		page.currPage = currPage;
		page.page = bills;
		page.totalCount = count;
		page.conditions = conditionMap;

		return page;
 	}
 	
 	/**
 	 * 我的会员账单--逾期账单
 	 * @param id
 	 * @param currPage
 	 * @param info
 	 * @return
 	 */
 	public static PageBean<v_bill_overdue> queryBillOverdue(long supervisorId, String yearStr, String monthStr, 
 			String typeStr, String key, String orderTypeStr, String currPageStr, String pageSizeStr, ErrorInfo error){
 		error.clear();
 		
 		int year = 0;
 		int month = 0;
 		int type = 0;
 		int orderType = 0;
 		int currPage = Constants.ONE;
 		int pageSize = Constants.TEN;
 		
 		if(NumberUtil.isNumericInt(yearStr)) {
 			year = Integer.parseInt(yearStr);
 		}
 		
 		if(NumberUtil.isNumericInt(monthStr)) {
 			month = Integer.parseInt(monthStr);
 		}
 		
 		if(NumberUtil.isNumericInt(typeStr)) {
 			type = Integer.parseInt(typeStr);
 		}
 		
 		if(NumberUtil.isNumericInt(orderTypeStr)) {
 			orderType = Integer.parseInt(orderTypeStr);
 		}
 		
 		if(NumberUtil.isNumericInt(currPageStr)) {
 			currPage = Integer.parseInt(currPageStr);
 		}
 		
 		if(NumberUtil.isNumericInt(pageSizeStr)) {
 			pageSize = Integer.parseInt(pageSizeStr);
 		}
 		
// 		if(year < 0 || year > 5) {
// 			year = 0;
//// 			error.code = -1;
//// 			error.msg = "选择的年份不在范围内";
//// 			
//// 			return null;
// 		}
 		
 		if(month < 0 || month > 12) {
 			month = 0;
// 			error.code = -2;
// 			error.msg = "选择的月份不在范围内";
// 			
// 			return null;
 		}
 		
 		if(type < 0 || type > 3) {
 			type = 0;
// 			error.code = -3;
// 			error.msg = "选择的查询类别不在范围内";
// 			
// 			return null;
 		}
 		
 		if(orderType < 0 || orderType > 6) {
 			orderType = 0;
// 			error.code = -3;
// 			error.msg = "选择的排序类别不在范围内";
// 			
// 			return null;
 		}
 		
 		Map<String,Object> conditionMap = new HashMap<String, Object>();
		
		conditionMap.put("year", year);
		conditionMap.put("month", month);
		conditionMap.put("type", type);
		conditionMap.put("key", key);
		conditionMap.put("orderType", orderType);
 		
		StringBuffer conditions = new StringBuffer("1=1 ");
		List<Object> values = new ArrayList<Object>();
		
		if(year != 0 ) {
			conditions.append("and year = ? ");
			values.add(year);
//			values.add(Constants.TIME_YEAR[year]);
		}
		
		if(month != 0) {
			conditions.append("and month = ? ");
			values.add(month);
//			values.add(Constants.TIME_MONTH[month]);
		}
		
		if(StringUtils.isNotBlank(key)) {
			conditions.append(Constants.C_TYPE[type]);
			
			if(type == 0 ) {
				values.add("%"+key+"%");
				values.add("%"+key+"%");
				values.add("%"+key+"%");
			}else {
				values.add("%"+key+"%");
			}
			
		}
		
		conditions.append("and supervisor_id = ? ");
		values.add(supervisorId);
		
		if(orderType != 0) {
			conditions.append("order by "+Constants.BILL_ORDER_Maturity[orderType]);
		}else{
			conditions.append("order by bid_id desc");
		}
 		
		List<v_bill_overdue> bills = new ArrayList<v_bill_overdue>();
		int count = 0;
		
		try {
			count = (int)v_bill_overdue.count(conditions.toString(), values.toArray());
			bills = v_bill_overdue.find(conditions.toString(), values.toArray()).fetch(currPage, pageSize);
		}catch (Exception e) {
			e.printStackTrace();
			Logger.info("查询我的会员账单--逾期账单情况时："+e.getMessage());
			error.code = -1;
			error.msg = "由于数据库异常，查询我的会员账单--逾期账单情况失败";
			
			return null;
		}
		
		PageBean<v_bill_overdue> page = new PageBean<v_bill_overdue>();
		
		page.pageSize = pageSize;
		page.currPage = currPage;
		page.page = bills;
		page.totalCount = count;
		page.conditions = conditionMap;

		return page;
 	}
 	
 	/**
 	 * 部门账单管理-逾期账单
 	 * @param id
 	 * @param currPage
 	 * @param info
 	 * @return
 	 */
 	public static PageBean<v_bill_department_overdue> queryBillDepartmentOverdue(int noPage, long supervisorId, String yearStr, String monthStr, 
 			String typeStr, String key, String kefuStr, String orderTypeStr, String currPageStr, String pageSizeStr, ErrorInfo error){
 		error.clear();
 		
 		int year = 0;
 		int month = 0;
 		int type = 0;
 		int kefu = 0;
 		int orderType = 0;
 		int currPage = Constants.ONE;
 		int pageSize = Constants.TEN;
 		
 		if(NumberUtil.isNumericInt(yearStr)) {
 			year = Integer.parseInt(yearStr);
 		}
 		
 		if(NumberUtil.isNumericInt(monthStr)) {
 			month = Integer.parseInt(monthStr);
 		}
 		
 		if(NumberUtil.isNumericInt(typeStr)) {
 			type = Integer.parseInt(typeStr);
 		}
 		
 		if(NumberUtil.isNumericInt(kefuStr)) {
 			kefu = Integer.parseInt(kefuStr);
 		}
 		
 		if(NumberUtil.isNumericInt(orderTypeStr)) {
 			orderType = Integer.parseInt(orderTypeStr);
 		}
 		
 		if(NumberUtil.isNumericInt(currPageStr)) {
 			currPage = Integer.parseInt(currPageStr);
 		}
 		
 		if(NumberUtil.isNumericInt(pageSizeStr)) {
 			pageSize = Integer.parseInt(pageSizeStr);
 		}
 		
// 		if(year < 0 || year > 5) {
// 			year = 0;
// 		}
 		
 		if(month < 0 || month > 12) {
 			month = 0;
 		}
 		
 		if(type < 0 || type > 3) {
 			type = 0;
 		}
 		
 		if(orderType < 0 || orderType > 10) {
 			orderType = 0;
 		}
		
 		Map<String,Object> conditionMap = new HashMap<String, Object>();
		
		conditionMap.put("year", year);
		conditionMap.put("month", month);
		conditionMap.put("type", type);
		conditionMap.put("key", key);
		conditionMap.put("kefu", kefu);
		conditionMap.put("orderType", orderType);
 		
		StringBuffer conditions = new StringBuffer("1=1 ");
		List<Object> values = new ArrayList<Object>();
		
		if(year != 0 ) {
			conditions.append("and year = ? ");
			values.add(year);
//			values.add(Constants.TIME_YEAR[year]);
		}
		
		if(month != 0) {
			conditions.append("and month = ? ");
			values.add(month);
//			values.add(Constants.TIME_MONTH[month]);
		}
		
		if(StringUtils.isNotBlank(key)) {
			conditions.append(Constants.C_TYPE[type]);
			
			if(type == 0 ) {
				values.add("%"+key+"%");
				values.add("%"+key+"%");
				values.add("%"+key+"%");
			}else {
				values.add("%"+key+"%");
			}
		}
		
		if(kefu != 0) {
			conditions.append("and supervisor_id = ? ");
			values.add(kefu);
		}
		
		if(orderType != 0) {
			conditions.append("order by "+Constants.BILL_ORDER_Maturity[orderType]);
		}else{
			conditions.append("order by bid_id desc");
		}
		
		List<v_bill_department_overdue> bills = new ArrayList<v_bill_department_overdue>();
		int count = 0;
		
		try {
			count = (int)v_bill_department_overdue.count(conditions.toString(), values.toArray());
			
			if(noPage != Constants.NO_PAGE){
				bills = v_bill_department_overdue.find(conditions.toString(), values.toArray()).fetch(currPage, pageSize);				
			}else{
				bills = v_bill_department_overdue.find(conditions.toString(), values.toArray()).fetch();
			}
		}catch (Exception e) {
			e.printStackTrace();
			Logger.info("查询部门账单管理-逾期账单情况时："+e.getMessage());
			error.code = -1;
			error.msg = "由于数据库异常，查询部门账单管理-逾期账单情况失败";
			
			return null;
		}
		
		PageBean<v_bill_department_overdue> page = new PageBean<v_bill_department_overdue>();
		
		page.pageSize = pageSize;
		page.currPage = currPage;
		page.page = bills;
		page.totalCount = count;
		page.conditions = conditionMap;

		return page;
 	}
 	
 	/**
 	 * 我的会员账单---已还款账单列表
 	 * @param id
 	 * @param currPage
 	 * @param info
 	 * @return
 	 */
 	public static PageBean<v_bill_haspayed> queryBillHasPayed(long supervisorId, String yearStr, String monthStr, 
 			String typeStr, String key, String orderTypeStr, String currPageStr, String pageSizeStr, ErrorInfo error){
 		error.clear();
 		
 		int year = 0;
 		int month = 0;
 		int type = 0;
 		int orderType = 0;
 		int currPage = Constants.ONE;
 		int pageSize = Constants.TEN;
 		
 		if(NumberUtil.isNumericInt(yearStr)) {
 			year = Integer.parseInt(yearStr);
 		}
 		
 		if(NumberUtil.isNumericInt(monthStr)) {
 			month = Integer.parseInt(monthStr);
 		}
 		
 		if(NumberUtil.isNumericInt(typeStr)) {
 			type = Integer.parseInt(typeStr);
 		}
 		
 		if(NumberUtil.isNumericInt(orderTypeStr)) {
 			orderType = Integer.parseInt(orderTypeStr);
 		}
 		
 		if(NumberUtil.isNumericInt(currPageStr)) {
 			currPage = Integer.parseInt(currPageStr);
 		}
 		
 		if(NumberUtil.isNumericInt(pageSizeStr)) {
 			pageSize = Integer.parseInt(pageSizeStr);
 		}
 		
// 		if(year < 0 || year > 5) {
// 			year = 0;
//// 			error.code = -1;
//// 			error.msg = "选择的年份不在范围内";
//// 			
//// 			return null;
// 		}
 		
 		if(month < 0 || month > 12) {
 			month = 0;
// 			error.code = -2;
// 			error.msg = "选择的月份不在范围内";
// 			
// 			return null;
 		}
 		
 		if(type < 0 || type > 3) {
 			type = 0;
// 			error.code = -3;
// 			error.msg = "选择的查询类别不在范围内";
// 			
// 			return null;
 		}
 		
 		if(orderType < 0 || orderType > 6) {
 			orderType = 0;
// 			error.code = -3;
// 			error.msg = "选择的排序类别不在范围内";
// 			
// 			return null;
 		}
 		
 		Map<String,Object> conditionMap = new HashMap<String, Object>();
		
		conditionMap.put("year", year);
		conditionMap.put("month", month);
		conditionMap.put("type", type);
		conditionMap.put("key", key);
		conditionMap.put("orderType", orderType);
 		
		StringBuffer conditions = new StringBuffer("1=1 ");
		List<Object> values = new ArrayList<Object>();
		
		if(year != 0 ) {
			conditions.append("and year = ? ");
			values.add(year);
//			values.add(Constants.TIME_YEAR[year]);
		}
		
		if(month != 0) {
			conditions.append("and month = ? ");
			values.add(month);
//			values.add(Constants.TIME_MONTH[month]);
		}
		
		if(StringUtils.isNotBlank(key)) {
			conditions.append(Constants.C_TYPE[type]);
			
			if(type == 0 ) {
				values.add("%"+key+"%");
				values.add("%"+key+"%");
				values.add("%"+key+"%");
			}else {
				values.add("%"+key+"%");
			}
			
		}
		
		conditions.append("and supervisor_id = ? ");
		values.add(supervisorId);
		
		if(orderType != 0) {
			conditions.append("order by "+Constants.BILL_ORDER_PAID[orderType]);
		}else{
			conditions.append("order by bid_id desc");
		}
 		
		List<v_bill_haspayed> bills = new ArrayList<v_bill_haspayed>();
		int count = 0;
		
		try {
			count = (int)v_bill_haspayed.count(conditions.toString(), values.toArray());
			bills = v_bill_haspayed.find(conditions.toString(), values.toArray()).fetch(currPage, pageSize);
		}catch (Exception e) {
			e.printStackTrace();
			Logger.info("查询我的会员账单---已还款账单列表情况时："+e.getMessage());
			error.code = -1;
			error.msg = "由于数据库异常，查询我的会员账单---已还款账单列表情况失败";
			
			return null;
		}
		
		PageBean<v_bill_haspayed> page = new PageBean<v_bill_haspayed>();
		
		page.pageSize = pageSize;
		page.currPage = currPage;
		page.page = bills;
		page.totalCount = count;
		page.conditions = conditionMap;

		return page;
 	}
 	
 	/**
 	 * 部门会员账单---已还款账单列表
 	 * @param id
 	 * @param currPage
 	 * @param info
 	 * @return
 	 */
 	public static PageBean<v_bill_department_haspayed> queryBillDepartmentHasPayed(int noPage, long supervisorId, String yearStr, String monthStr, 
 			String typeStr, String key, String kefuStr, String orderTypeStr, String currPageStr, String pageSizeStr, ErrorInfo error){
 		error.clear();
 		
 		int year = 0;
 		int month = 0;
 		int type = 0;
 		int kefu = 0;
 		int orderType = 0;
 		int currPage = Constants.ONE;
 		int pageSize = Constants.TEN;
 		
 		if(NumberUtil.isNumericInt(yearStr)) {
 			year = Integer.parseInt(yearStr);
 		}
 		
 		if(NumberUtil.isNumericInt(monthStr)) {
 			month = Integer.parseInt(monthStr);
 		}
 		
 		if(NumberUtil.isNumericInt(typeStr)) {
 			type = Integer.parseInt(typeStr);
 		}
 		
 		if(NumberUtil.isNumericInt(kefuStr)) {
 			kefu = Integer.parseInt(kefuStr);
 		}
 		
 		if(NumberUtil.isNumericInt(orderTypeStr)) {
 			orderType = Integer.parseInt(orderTypeStr);
 		}
 		
 		if(NumberUtil.isNumericInt(currPageStr)) {
 			currPage = Integer.parseInt(currPageStr);
 		}
 		
 		if(NumberUtil.isNumericInt(pageSizeStr)) {
 			pageSize = Integer.parseInt(pageSizeStr);
 		}
 		
// 		if(year < 0 || year > 5) {
// 			year = 0;
// 		}
 		
 		if(month < 0 || month > 12) {
 			month = 0;
 		}
 		
 		if(type < 0 || type > 3) {
 			type = 0;
 		}
 		
 		if(orderType < 0 || orderType > 10) {
 			orderType = 0;
 		}
		
 		Map<String,Object> conditionMap = new HashMap<String, Object>();
		
		conditionMap.put("year", year);
		conditionMap.put("month", month);
		conditionMap.put("type", type);
		conditionMap.put("key", key);
		conditionMap.put("kefu", kefu);
		conditionMap.put("orderType", orderType);
 		
		StringBuffer conditions = new StringBuffer();
		List<Object> values = new ArrayList<Object>();
		
		StringBuffer sql = new StringBuffer();
		sql.append("select `a`.`id` AS `id`,`c`.`id` AS `user_id`,year(`a`.`repayment_time`) AS `year`,month(`a`.`repayment_time`) AS `month`,`b`.`id` AS `bid_id`,concat(`e`.`_value`,cast(`a`.`id` as char charset utf8)) AS `bill_no`,`c`.`name` AS `name`,concat(`f`.`_value`,cast(`b`.`id` as char charset utf8)) AS `bid_no`,`b`.`amount` AS `amount`,`b`.`apr` AS `apr`,`a`.`title` AS `title`,(select concat(`a`.`periods`,'/',count(`t`.`id`)) from `t_bills` `t` where (`t`.`bid_id` = `a`.`bid_id`)) AS `period`,`a`.`repayment_time` AS `repayment_time`,`a`.`real_repayment_time` AS `real_repayment_time`,(select `a`.`name` AS `name` from `t_supervisors` `a` where (`c`.`assigned_to_supervisor_id` = `a`.`id`)) AS `supervisor_name`,(select `a`.`name` AS `name` from `t_supervisors` `a` where (`b`.`manage_supervisor_id` = `a`.`id`)) AS `supervisor_name2` from ((((`t_bills` `a` join `t_bids` `b` on((`a`.`bid_id` = `b`.`id`))) join `t_users` `c` on((`b`.`user_id` = `c`.`id`))) join `t_system_options` `f`) join `t_system_options` `e`) where ((`e`.`_key` = 'loan_bill_number') and (`f`.`_key` = 'loan_number') and (`a`.`status` in (0,-(3)))) ");
		
		if(year != 0 ) {
			conditions.append("AND YEAR (`a`.`repayment_time`) = ? ");
			values.add(year);
//			values.add(Constants.TIME_YEAR[year]);
		}
		
		if(month != 0) {
			conditions.append("AND MONTH (`a`.`repayment_time`) = ? ");
			values.add(month);
//			values.add(Constants.TIME_MONTH[month]);
		}
		
		if(StringUtils.isNotBlank(key)) {
			conditions.append(BillOZ.C_TYPE[type]);
			
			if(type == 0 ) {
				values.add("%"+key+"%");
				values.add("%"+key+"%");
				values.add("%"+key+"%");
			}else {
				values.add("%"+key+"%");
			}
		}
		
		if(kefu != 0) {
			conditions.append("and supervisor_id = ? ");
			values.add(kefu);
		}
		
		if(orderType != 0) {
			conditions.append("order by "+Constants.BILL_ORDER_PAID[orderType]);
		}else {
			conditions.append("order by bid_id desc");
		}
 		
		List<v_bill_department_haspayed> bills = new ArrayList<v_bill_department_haspayed>();
		int count = 0;
		
		sql.append(conditions);
		
		EntityManager em = JPA.em();
		Query query = em.createNativeQuery(sql.toString(), v_bill_department_haspayed.class);
		
		for (int i = 0; i < values.size(); i++) {
			query.setParameter(i + 1, values.get(i));
		}
		
		if(noPage != Constants.NO_PAGE){
			query.setFirstResult((currPage - 1) * pageSize);
			query.setMaxResults(pageSize);
		}
		
		try {
			count = QueryUtil.getQueryCountByCondition(em, sql.toString(), values);
			bills = query.getResultList();
		}catch (Exception e) {
			e.printStackTrace();
			Logger.info("查询我的会员账单---已还款账单列表情况时："+e.getMessage());
			error.code = -1;
			error.msg = "由于数据库异常，查询我的会员账单---已还款账单列表情况失败";
			
			return null;
		}
		
		PageBean<v_bill_department_haspayed> page = new PageBean<v_bill_department_haspayed>();
		
		page.pageSize = pageSize;
		page.currPage = currPage;
		page.page = bills;
		page.totalCount = count;
		page.conditions = conditionMap;

		return page;
 	}
 	
 	/**
 	 * 应收账单管理--待收款借款账单列表
 	 * @param id
 	 * @param currPage
 	 * @param info
 	 * @return
 	 */
 	public static PageBean<v_bill_receiving> queryBillReceiving(long supervisorId, String yearStr, String monthStr, 
 			String typeStr, String key, String orderTypeStr, String currPageStr, String pageSizeStr, ErrorInfo error) {
 		error.clear();
 		
 		int year = 0;
 		int month = 0;
 		int type = 0;
 		int orderType = 0;
 		int currPage = Constants.ONE;
 		int pageSize = Constants.TEN;
 		
 		if(NumberUtil.isNumericInt(yearStr)) {
 			year = Integer.parseInt(yearStr);
 		}
 		
 		if(NumberUtil.isNumericInt(monthStr)) {
 			month = Integer.parseInt(monthStr);
 		}
 		
 		if(NumberUtil.isNumericInt(typeStr)) {
 			type = Integer.parseInt(typeStr);
 		}
 		
 		if(NumberUtil.isNumericInt(orderTypeStr)) {
 			orderType = Integer.parseInt(orderTypeStr);
 		}
 		
 		if(NumberUtil.isNumericInt(currPageStr)) {
 			currPage = Integer.parseInt(currPageStr);
 		}
 		
 		if(NumberUtil.isNumericInt(pageSizeStr)) {
 			pageSize = Integer.parseInt(pageSizeStr);
 		}
 		
 		if(month < 0 || month > 12) {
 			month = 0;
 		}
 		
 		if(type < 0 || type > 3) {
 			type = 0;
 		}
 		
 		if(orderType < 0 || orderType > 6) {
 			orderType = 0;
 		}
 		
 		Map<String,Object> conditionMap = new HashMap<String, Object>();
		
		conditionMap.put("year", year);
		conditionMap.put("month", month);
		conditionMap.put("type", type);
		conditionMap.put("key", key);
		conditionMap.put("orderType", orderType);
 		
		StringBuffer conditions = new StringBuffer("1=1 ");
		List<Object> values = new ArrayList<Object>();
		
		if(year != 0 ) {
			conditions.append("and year = ? ");
			values.add(year);
		}
		
		if(month != 0) {
			conditions.append("and month = ? ");
			values.add(month);
		}
		
		if(StringUtils.isNotBlank(key)) {
			conditions.append(Constants.C_TYPE[type]);
			
			if(type == 0 ) {
				values.add("%"+key+"%");
				values.add("%"+key+"%");
				values.add("%"+key+"%");
			}else {
				values.add("%"+key+"%");
			}	
		}
		
		conditions.append("order by "+Constants.BILL_ORDER_Maturity[orderType]);
		
		List<v_bill_receiving> bills = new ArrayList<v_bill_receiving>();
		int count = 0;
		
		try {
			count = (int)v_bill_receiving.count(conditions.toString(), values.toArray());
			bills = v_bill_receiving.find(conditions.toString(), values.toArray()).fetch(currPage, pageSize);
		}catch (Exception e) {
			e.printStackTrace();
			Logger.info("查询应收账单管理--待收款借款账单列表情况时："+e.getMessage());
			error.code = -1;
			error.msg = "由于数据库异常，查询应收账单管理--待收款借款账单列表情况失败";
			
			return null;
		}
		
		PageBean<v_bill_receiving> page = new PageBean<v_bill_receiving>();
		
		page.pageSize = pageSize;
		page.currPage = currPage;
		page.page = bills;
		page.totalCount = count;
		page.conditions = conditionMap;

		return page;
 	}
 	
	/**
 	 * 应收账单管理--逾期账单列表
 	 * @param id
 	 * @param currPage
 	 * @param info
 	 * @return
 	 */
 	public static PageBean<v_bill_receiving_overdue> queryBillReceivingOverdue(long supervisorId, String yearStr, String monthStr, 
 			String typeStr, String key, String orderTypeStr, String currPageStr, String pageSizeStr, ErrorInfo error){
 		error.clear();
 		
 		int year = 0;
 		int month = 0;
 		int type = 0;
 		int orderType = 0;
 		int currPage = Constants.ONE;
 		int pageSize = Constants.TEN;
 		
 		if(NumberUtil.isNumericInt(yearStr)) {
 			year = Integer.parseInt(yearStr);
 		}
 		
 		if(NumberUtil.isNumericInt(monthStr)) {
 			month = Integer.parseInt(monthStr);
 		}
 		
 		if(NumberUtil.isNumericInt(typeStr)) {
 			type = Integer.parseInt(typeStr);
 		}
 		
 		if(NumberUtil.isNumericInt(orderTypeStr)) {
 			orderType = Integer.parseInt(orderTypeStr);
 		}
 		
 		if(NumberUtil.isNumericInt(currPageStr)) {
 			currPage = Integer.parseInt(currPageStr);
 		}
 		
 		if(NumberUtil.isNumericInt(pageSizeStr)) {
 			pageSize = Integer.parseInt(pageSizeStr);
 		}
 		
 		if(month < 0 || month > 12) {
 			month = 0;
 		}
 		
 		if(type < 0 || type > 3) {
 			type = 0;
 		}
 		
 		if(orderType < 0 || orderType > 10) {
 			orderType = 0;
 		}
 		
 		Map<String,Object> conditionMap = new HashMap<String, Object>();
		
		conditionMap.put("year", year);
		conditionMap.put("month", month);
		conditionMap.put("type", type);
		conditionMap.put("key", key);
		conditionMap.put("orderType", orderType);
 		
		StringBuffer conditions = new StringBuffer("1=1 ");
		List<Object> values = new ArrayList<Object>();
		
		if(year != 0 ) {
			conditions.append("and year = ? ");
			values.add(year);
		}
		
		if(month != 0) {
			conditions.append("and month = ? ");
			values.add(month);
		}
		
		if(StringUtils.isNotBlank(key)) {
			conditions.append(Constants.C_TYPE[type]);
			
			if(type == 0 ) {
				values.add("%"+key+"%");
				values.add("%"+key+"%");
				values.add("%"+key+"%");
			}else {
				values.add("%"+key+"%");
			}	
		}
		
		conditions.append("order by "+Constants.BILL_ORDER_OVERDUE[orderType]);
		
		List<v_bill_receiving_overdue> bills = new ArrayList<v_bill_receiving_overdue>();
		int count = 0;
		
		try {
			count = (int)v_bill_receiving_overdue.count(conditions.toString(), values.toArray());
			bills = v_bill_receiving_overdue.find(conditions.toString(), values.toArray()).fetch(currPage, pageSize);
		}catch (Exception e) {
			e.printStackTrace();
			Logger.info("查询应收账单管理--逾期账单列表情况时："+e.getMessage());
			error.code = -1;
			error.msg = "由于数据库异常，查询应收账单管理--逾期账单列表情况失败";
			
			return null;
		}
		
		PageBean<v_bill_receiving_overdue> page = new PageBean<v_bill_receiving_overdue>();
		
		page.pageSize = pageSize;
		page.currPage = currPage;
		page.page = bills;
		page.totalCount = count;
		page.conditions = conditionMap;

		return page;
 	}
 	
 	/**
 	 * 应收账单管理--已收款借款账单列表
 	 * @param id
 	 * @param currPage
 	 * @param info
 	 * @return
 	 */
 	public static PageBean<v_bill_has_received> queryBillHasReceived(long supervisorId, String yearStr, String monthStr, 
 			String typeStr, String key, String orderTypeStr, String currPageStr, String pageSizeStr, ErrorInfo error) {
 		error.clear();
 		
 		int year = 0;
 		int month = 0;
 		int type = 0;
 		int orderType = 0;
 		int currPage = Constants.ONE;
 		int pageSize = Constants.TEN;
 		
 		if(NumberUtil.isNumericInt(yearStr)) {
 			year = Integer.parseInt(yearStr);
 		}
 		
 		if(NumberUtil.isNumericInt(monthStr)) {
 			month = Integer.parseInt(monthStr);
 		}
 		
 		if(NumberUtil.isNumericInt(typeStr)) {
 			type = Integer.parseInt(typeStr);
 		}
 		
 		if(NumberUtil.isNumericInt(orderTypeStr)) {
 			orderType = Integer.parseInt(orderTypeStr);
 		}
 		
 		if(NumberUtil.isNumericInt(currPageStr)) {
 			currPage = Integer.parseInt(currPageStr);
 		}
 		
 		if(NumberUtil.isNumericInt(pageSizeStr)) {
 			pageSize = Integer.parseInt(pageSizeStr);
 		}
 		
 		if(month < 0 || month > 12) {
 			month = 0;
 		}
 		
 		if(type < 0 || type > 3) {
 			type = 0;
 		}
 		
 		if(orderType < 0 || orderType > 10) {
 			orderType = 0;
 		}
 		
 		Map<String,Object> conditionMap = new HashMap<String, Object>();
		
		conditionMap.put("year", year);
		conditionMap.put("month", month);
		conditionMap.put("type", type);
		conditionMap.put("key", key);
		conditionMap.put("orderType", orderType);
 		
		StringBuffer conditions = new StringBuffer("1=1 ");
		List<Object> values = new ArrayList<Object>();
		
		if(year != 0 ) {
			conditions.append("and year = ? ");
			values.add(year);
		}
		
		if(month != 0) {
			conditions.append("and month = ? ");
			values.add(month);
		}
		
		if(StringUtils.isNotBlank(key)) {
			conditions.append(Constants.C_TYPE[type]);
			
			if(type == 0 ) {
				values.add("%"+key+"%");
				values.add("%"+key+"%");
				values.add("%"+key+"%");
			}else {
				values.add("%"+key+"%");
			}	
		}
		
		conditions.append("order by "+Constants.BILL_ORDER_RECEIVE[orderType]);
		
		List<v_bill_has_received> bills = new ArrayList<v_bill_has_received>();
		int count = 0;
		
		try {
			count = (int)v_bill_has_received.count(conditions.toString(), values.toArray());
			bills = v_bill_has_received.find(conditions.toString(), values.toArray()).fetch(currPage, pageSize);
		}catch (Exception e) {
			e.printStackTrace();
			Logger.info("查询应收账单管理--逾期账单列表情况时："+e.getMessage());
			error.code = -1;
			error.msg = "由于数据库异常，查询应收账单管理--逾期账单列表情况失败";
			
			return null;
		}
		
		PageBean<v_bill_has_received> page = new PageBean<v_bill_has_received>();
		
		page.pageSize = pageSize;
		page.currPage = currPage;
		page.page = bills;
		page.totalCount = count;
		page.conditions = conditionMap;

		return page;
 	}
 	
 	/**
 	 * 应收账单管理--应收款借款账单统计表
 	 * @param id
 	 * @param currPage
 	 * @param info
 	 * @return
 	 */
 	public static PageBean<v_bill_receviable_statistical> queryBillReceivedStatical(int noPage, long supervisorId, String yearStr, String monthStr, 
 			String orderTypeStr, String currPageStr, String pageSizeStr, ErrorInfo error) {
 		error.clear();
 		
 		int year = 0;
 		int month = 0;
 		int orderType = 0;
 		int currPage = Constants.ONE;
 		int pageSize = Constants.TEN;
 		
 		if(NumberUtil.isNumericInt(yearStr)) {
 			year = Integer.parseInt(yearStr);
 		}
 		
 		if(NumberUtil.isNumericInt(monthStr)) {
 			month = Integer.parseInt(monthStr);
 		}
 		
 		
 		if(NumberUtil.isNumericInt(orderTypeStr)) {
 			orderType = Integer.parseInt(orderTypeStr);
 		}
 		
 		if(NumberUtil.isNumericInt(currPageStr)) {
 			currPage = Integer.parseInt(currPageStr);
 		}
 		
 		if(NumberUtil.isNumericInt(pageSizeStr)) {
 			pageSize = Integer.parseInt(pageSizeStr);
 		}
 		
 		if(month < 0 || month > 12) {
 			month = 0;
 		}
 		
 		if(orderType < 0 || orderType > 10) {
 			orderType = 0;
 		}
 		
 		Map<String,Object> conditionMap = new HashMap<String, Object>();
		
		conditionMap.put("year", year);
		conditionMap.put("month", month);
		conditionMap.put("orderType", orderType);
 		
		StringBuffer conditions = new StringBuffer("1=1 ");
		List<Object> values = new ArrayList<Object>();
		
		if(year != 0 ) {
			conditions.append("and year = ? ");
			values.add(year);
		}
		
		if(month != 0) {
			conditions.append("and month = ? ");
			values.add(month);
		}
		
		
		conditions.append("order by "+Constants.BILL_ORDER_RECEVIABLE[orderType]);
		
		List<v_bill_receviable_statistical> bills = new ArrayList<v_bill_receviable_statistical>();
		int count = 0;
		
		try {
			count = (int)v_bill_receviable_statistical.count(conditions.toString(), values.toArray());
			
			if(noPage == Constants.NO_PAGE){
				bills = v_bill_receviable_statistical.find(conditions.toString(), values.toArray()).fetch();
			}else{
				bills = v_bill_receviable_statistical.find(conditions.toString(), values.toArray()).fetch(currPage, pageSize);
			}
		}catch (Exception e) {
			e.printStackTrace();
			Logger.info("查询应收账单管理--应收款借款账单统计表情况时："+e.getMessage());
			error.code = -1;
			error.msg = "由于数据库异常，查询应收账单管理--应收款借款账单统计表情况失败";
			
			return null;
		}
		
		PageBean<v_bill_receviable_statistical> page = new PageBean<v_bill_receviable_statistical>();
		
		page.pageSize = pageSize;
		page.currPage = currPage;
		page.page = bills;
		page.totalCount = count;
		page.conditions = conditionMap;

		return page;
 	}
 	
	/**
 	 * 应付账单管理--待付款理财账单列表
 	 * @param id
 	 * @param currPage
 	 * @param info
 	 * @return
 	 */
 	public static PageBean<v_bill_invests_pending_payment> queryBillInvestPending(long supervisorId, String yearStr, String monthStr, 
 			String typeStr, String key, String orderTypeStr, String currPageStr, String pageSizeStr, ErrorInfo error) {
 		error.clear();
 		
 		int year = 0;
 		int month = 0;
 		int type = 0;
 		int orderType = 0;
 		int currPage = Constants.ONE;
 		int pageSize = Constants.TEN;
 		
 		if(NumberUtil.isNumericInt(yearStr)) {
 			year = Integer.parseInt(yearStr);
 		}
 		
 		if(NumberUtil.isNumericInt(monthStr)) {
 			month = Integer.parseInt(monthStr);
 		}
 		
 		if(NumberUtil.isNumericInt(typeStr)) {
 			type = Integer.parseInt(typeStr);
 		}
 		
 		if(NumberUtil.isNumericInt(orderTypeStr)) {
 			orderType = Integer.parseInt(orderTypeStr);
 		}
 		
 		if(NumberUtil.isNumericInt(currPageStr)) {
 			currPage = Integer.parseInt(currPageStr);
 		}
 		
 		if(NumberUtil.isNumericInt(pageSizeStr)) {
 			pageSize = Integer.parseInt(pageSizeStr);
 		}
 		
 		if(month < 0 || month > 12) {
 			month = 0;
 		}
 		
 		if(type < 0 || type > 3) {
 			type = 0;
 		}
 		
 		if(orderType < 0 || orderType > 10) {
 			orderType = 0;
 		}
 		
 		Map<String,Object> conditionMap = new HashMap<String, Object>();
		
		conditionMap.put("year", year);
		conditionMap.put("month", month);
		conditionMap.put("type", type);
		conditionMap.put("key", key);
		conditionMap.put("orderType", orderType);
 		
		StringBuffer conditions = new StringBuffer(" where 1=1 ");
		List<Object> values = new ArrayList<Object>();
		
		if(year != 0 ) {
			conditions.append("and year = ? ");
			values.add(year);
		}
		
		if(month != 0) {
			conditions.append("and month = ? ");
			values.add(month);
		}
		
		if(StringUtils.isNotBlank(key)) {
			conditions.append(Constants.C_TYPE[type]);
			
			if(type == 0 ) {
				values.add("%"+key+"%");
				values.add("%"+key+"%");
				values.add("%"+key+"%");
			}else {
				values.add("%"+key+"%");
			}	
		}
		
		List<v_bill_invests_pending_payment> bills = new ArrayList<v_bill_invests_pending_payment>();
		int count = 0;
		
		try {
			EntityManager em = JPA.em();
			String sql = "SELECT count(1) FROM ( SELECT a.id AS id, YEAR (a.receive_time) AS YEAR, MONTH (a.receive_time) AS MONTH, concat( f._value, cast(a.id AS CHAR charset utf8) ) AS bill_no, d. NAME AS invest_name, a.title AS title, a.id AS invest_id, b.id AS bid_id, concat( g._value, cast(b.id AS CHAR charset utf8) ) AS bid_no, e. NAME AS NAME, a.receive_time AS receive_time, a. STATUS AS STATUS FROM t_bill_invests a, t_bids b, t_users d, t_users e, t_system_options f, t_system_options g WHERE a.bid_id = b.id AND a.user_id = d.id AND b.user_id = e.id AND f._key = 'invests_bill_number' AND g._key = 'loan_number' AND a. STATUS IN (- 1 ,- 2 ,- 5 ,- 6) ) x";
			Query query1 = em.createNativeQuery(sql + conditions);
			for(int n = 1; n <= values.size(); n++){
				query1.setParameter(n, values.get(n-1));
			}
			count = Convert.strToInt(query1.getSingleResult().toString(), 0);
			
			
			conditions.append("order by "+Constants.BILL_ORDER_REPAYMENT[orderType]);
			sql = "SELECT * FROM ( SELECT a.id AS id, YEAR (a.receive_time) AS YEAR, MONTH (a.receive_time) AS MONTH, concat( f._value, cast(a.id AS CHAR charset utf8) ) AS bill_no, d. NAME AS invest_name, ( SELECT concat(a.periods, '/', count(t.id)) FROM t_bills t WHERE t.bid_id = a.bid_id ) AS period, ( a.receive_corpus + a.receive_interest + a.overdue_fine ) AS pay_amount, a.title AS title, a.id AS invest_id, b.id AS bid_id, concat( g._value, cast(b.id AS CHAR charset utf8) ) AS bid_no, e. NAME AS NAME, a.receive_time AS receive_time, ( CASE WHEN ((a.receive_time - now()) > 0) THEN 0 ELSE datediff( now(), a.receive_time ) END ) AS overdue_time, a. STATUS AS STATUS, ( SELECT a. NAME AS NAME FROM t_supervisors a WHERE d.assigned_to_supervisor_id = a.id ) AS supervisor_name, ( SELECT a. NAME AS NAME FROM t_supervisors a WHERE b.manage_supervisor_id = a.id ) AS supervisor_name2,a.user_id FROM t_bill_invests a, t_bids b, t_users d, t_users e, t_system_options f, t_system_options g WHERE a.bid_id = b.id AND a.user_id = d.id AND b.user_id = e.id AND f._key = 'invests_bill_number' AND g._key = 'loan_number' AND a. STATUS IN (- 1 ,- 2 ,- 5 ,- 6) )x";

            Query query = em.createNativeQuery(sql + conditions,v_bill_invests_pending_payment.class);
            for(int n = 1; n <= values.size(); n++){
                query.setParameter(n, values.get(n-1));
            }
            query.setFirstResult((currPage - 1) * pageSize);
            query.setMaxResults(pageSize);
            bills = query.getResultList();
		}catch (Exception e) {
			e.printStackTrace();
			Logger.info("查询应收账单管理--已收款借款账单列表情况时："+e.getMessage());
			error.code = -1;
			error.msg = "由于数据库异常，查询应收账单管理--已收款借款账单列表情况失败";
			
			return null;
		}
		
		PageBean<v_bill_invests_pending_payment> page = new PageBean<v_bill_invests_pending_payment>();
		
		page.pageSize = pageSize;
		page.currPage = currPage;
		page.page = bills;
		page.totalCount = count;
		page.conditions = conditionMap;

		return page;
 	}
 	
	/**
 	 * 应付账单管理--逾期未付理财账单列表
 	 * @param id
 	 * @param currPage
 	 * @param info
 	 * @return
 	 */
 	public static PageBean<v_bill_invests_overdue_unpaid> queryBillOverdueUnpaid(long supervisorId, String yearStr, String monthStr, 
 			String typeStr, String key, String orderTypeStr, String currPageStr, String pageSizeStr, ErrorInfo error) {
 		error.clear();
 		
 		int year = 0;
 		int month = 0;
 		int type = 0;
 		int orderType = 0;
 		int currPage = Constants.ONE;
 		int pageSize = Constants.TEN;
 		
 		if(NumberUtil.isNumericInt(yearStr)) {
 			year = Integer.parseInt(yearStr);
 		}
 		
 		if(NumberUtil.isNumericInt(monthStr)) {
 			month = Integer.parseInt(monthStr);
 		}
 		
 		if(NumberUtil.isNumericInt(typeStr)) {
 			type = Integer.parseInt(typeStr);
 		}
 		
 		if(NumberUtil.isNumericInt(orderTypeStr)) {
 			orderType = Integer.parseInt(orderTypeStr);
 		}
 		
 		if(NumberUtil.isNumericInt(currPageStr)) {
 			currPage = Integer.parseInt(currPageStr);
 		}
 		
 		if(NumberUtil.isNumericInt(pageSizeStr)) {
 			pageSize = Integer.parseInt(pageSizeStr);
 		}
 		
 		if(month < 0 || month > 12) {
 			month = 0;
 		}
 		
 		if(type < 0 || type > 3) {
 			type = 0;
 		}
 		
 		if(orderType < 0 || orderType > 10) {
 			orderType = 0;
 		}
 		
 		Map<String,Object> conditionMap = new HashMap<String, Object>();
		
		conditionMap.put("year", year);
		conditionMap.put("month", month);
		conditionMap.put("type", type);
		conditionMap.put("key", key);
		conditionMap.put("orderType", orderType);
 		
		StringBuffer conditions = new StringBuffer(" where 1=1 ");
		List<Object> values = new ArrayList<Object>();
		
		if(year != 0 ) {
			conditions.append("and year = ? ");
			values.add(year);
		}
		
		if(month != 0) {
			conditions.append("and month = ? ");
			values.add(month);
		}
		
		if(StringUtils.isNotBlank(key)) {
			conditions.append(Constants.C_TYPE[type]);
			
			if(type == 0 ) {
				values.add("%"+key+"%");
				values.add("%"+key+"%");
				values.add("%"+key+"%");
			}else {
				values.add("%"+key+"%");
			}	
		}
		
		
		List<v_bill_invests_overdue_unpaid> bills = new ArrayList<v_bill_invests_overdue_unpaid>();
		int count = 0;
		
		try {
			EntityManager em = JPA.em();
			String sql = "SELECT count(1) FROM ( SELECT a.id AS id, u.id AS bill_id, u. STATUS AS bill_status, a.periods AS bill_period, YEAR (a.receive_time) AS YEAR, MONTH (a.receive_time) AS MONTH, concat( f._value, cast(a.id AS CHAR charset utf8) ) AS bill_no, concat( g._value, cast(b.id AS CHAR charset utf8) ) AS bid_no, d. NAME AS invest_name, a.title AS title, b.id AS bid_id, e. NAME AS NAME, a.receive_time AS receive_time FROM t_bill_invests a, t_bids b, t_bills u, t_users e, t_users d, t_system_options f, t_system_options g WHERE a.bid_id = b.id AND a.bid_id = u.bid_id AND a.periods = u.periods AND d.id = a.user_id AND b.user_id = e.id AND a. STATUS = - 2 AND f._key = 'invests_bill_number' AND g._key = 'loan_number' ) x";
			Query query1 = em.createNativeQuery(sql + conditions);
			for(int n = 1; n <= values.size(); n++){
				query1.setParameter(n, values.get(n-1));
			}
			count = Convert.strToInt(query1.getSingleResult().toString(), 0);
			
			
			conditions.append("order by "+Constants.BILL_ORDER_REPAYMENT[orderType]);
			sql = "SELECT * FROM ( SELECT a.id AS id, u.id AS bill_id, u. STATUS AS bill_status, a.periods AS bill_period, YEAR (a.receive_time) AS YEAR, MONTH (a.receive_time) AS MONTH, concat( f._value, cast(a.id AS CHAR charset utf8) ) AS bill_no, d. NAME AS invest_name, a.title AS title, b.id AS bid_id, e. NAME AS NAME, a.receive_time AS receive_time, concat( g._value, cast(b.id AS CHAR charset utf8) ) AS bid_no, ( SELECT sum( ( ( t.receive_corpus + t.receive_interest ) + ifnull(t.overdue_fine, 0) ) ) FROM t_bill_invests t WHERE (t.id = a.id) ) AS pay_amount, ( SELECT concat(a.periods, '/', count(t.id)) FROM t_bills t WHERE (t.bid_id = a.bid_id) ) AS period,( CASE WHEN ((a.receive_time - now()) > 0) THEN 0 ELSE datediff(NOW(), a.receive_time) END ) AS overdue_time, ( SELECT count(t.id) FROM t_bill_invests t WHERE t. STATUS IN (- 2 ,- 6) AND t.user_id = a.user_id ) AS unpaid_bills,s. NAME AS supervisor_name, bs. NAME AS supervisor_name2, b.is_register_guarantor as is_register_guarantor FROM t_bill_invests a, t_bids b LEFT JOIN t_supervisors bs ON b.manage_supervisor_id = bs.id, t_bills u, t_users e LEFT JOIN t_supervisors s ON e.assigned_to_supervisor_id = s.id, t_users d , t_system_options f, t_system_options g WHERE a.bid_id = b.id AND a.bid_id = u.bid_id AND a.periods = u.periods AND d.id = a.user_id AND b.user_id = e.id AND a. STATUS = - 2 AND f._key = 'invests_bill_number' AND g._key = 'loan_number' ) x";

            Query query = em.createNativeQuery(sql + conditions,v_bill_invests_overdue_unpaid.class);
            for(int n = 1; n <= values.size(); n++){
                query.setParameter(n, values.get(n-1));
            }
            query.setFirstResult((currPage - 1) * pageSize);
            query.setMaxResults(pageSize);
            bills = query.getResultList();
		}catch (Exception e) {
			e.printStackTrace();
			Logger.info("查询应付账单管理--逾期未付理财账单列表情况时："+e.getMessage());
			error.code = -1;
			error.msg = "由于数据库异常，查询应付账单管理--逾期未付理财账单列表情况失败";
			
			return null;
		}
		
		PageBean<v_bill_invests_overdue_unpaid> page = new PageBean<v_bill_invests_overdue_unpaid>();
		
		page.pageSize = pageSize;
		page.currPage = currPage;
		page.page = bills;
		page.totalCount = count;
		page.conditions = conditionMap;

		return page;
 	}
 	
 	/**
 	 * 应付账单管理--已付款理财账单列表
 	 * @param id
 	 * @param currPage
 	 * @param info
 	 * @return
 	 */
 	public static PageBean<v_bill_invests_paid> queryBillInvestPaid(long supervisorId, String yearStr, String monthStr, 
 			String typeStr, String key, String paidTypeStr, String orderTypeStr, String currPageStr,
 			String pageSizeStr, ErrorInfo error) {
 		error.clear();
 		
 		int year = 0;
 		int month = 0;
 		int type = 0;
 		int paidType = 0;
 		int orderType = 0;
 		int currPage = Constants.ONE;
 		int pageSize = Constants.TEN;
 		
 		if(NumberUtil.isNumericInt(yearStr)) {
 			year = Integer.parseInt(yearStr);
 		}
 		
 		if(NumberUtil.isNumericInt(monthStr)) {
 			month = Integer.parseInt(monthStr);
 		}
 		
 		if(NumberUtil.isNumericInt(typeStr)) {
 			type = Integer.parseInt(typeStr);
 		}
 		
 		if(NumberUtil.isNumericInt(paidTypeStr)) {
 			paidType = Integer.parseInt(paidTypeStr);
 		}
 		
 		if(NumberUtil.isNumericInt(orderTypeStr)) {
 			orderType = Integer.parseInt(orderTypeStr);
 		}
 		
 		if(NumberUtil.isNumericInt(currPageStr)) {
 			currPage = Integer.parseInt(currPageStr);
 		}
 		
 		if(NumberUtil.isNumericInt(pageSizeStr)) {
 			pageSize = Integer.parseInt(pageSizeStr);
 		}
 		
 		if(month < 0 || month > 12) {
 			month = 0;
 		}
 		
 		if(type < 0 || type > 3) {
 			type = 0;
 		}
 		
 		if(type < 0 || type > 2) {
 			type = 0;
 		}
 		
 		if(orderType < 0 || orderType > 6) {
 			orderType = 0;
 		}
 		
 		Map<String,Object> conditionMap = new HashMap<String, Object>();
		
		conditionMap.put("year", year);
		conditionMap.put("month", month);
		conditionMap.put("type", type);
		conditionMap.put("key", key);
		conditionMap.put("paidType", paidType);
		conditionMap.put("orderType", orderType);
 		
		StringBuffer conditions = new StringBuffer("1=1 ");
		List<Object> values = new ArrayList<Object>();
		
		if(year != 0 ) {
			conditions.append("and year = ? ");
			values.add(year);
		}
		
		if(month != 0) {
			conditions.append("and month = ? ");
			values.add(month);
		}
		
		if(StringUtils.isNotBlank(key)) {
			conditions.append(Constants.C_TYPE[type]);
			
			if(type == 0 ) {
				values.add("%"+key+"%");
				values.add("%"+key+"%");
				values.add("%"+key+"%");
			}else {
				values.add("%"+key+"%");
			}	
		}
		
		conditions.append(Constants.BILL_ORDER_STATUS[paidType]);
		conditions.append("order by "+Constants.INVEST_BILL_HAS_PAID[orderType]);
		
		List<v_bill_invests_paid> bills = new ArrayList<v_bill_invests_paid>();
		int count = 0;
		
		try {
			count = (int)v_bill_invests_paid.count(conditions.toString(), values.toArray());
			bills = v_bill_invests_paid.find(conditions.toString(), values.toArray()).fetch(currPage, pageSize);
		}catch (Exception e) {
			e.printStackTrace();
			Logger.info("查询应付账单管理--已付款理财账单列表情况时："+e.getMessage());
			error.code = -1;
			error.msg = "由于数据库异常，查询应付账单管理--已付款理财账单列表情况失败";
			
			return null;
		}
		
		PageBean<v_bill_invests_paid> page = new PageBean<v_bill_invests_paid>();
		
		page.pageSize = pageSize;
		page.currPage = currPage;
		page.page = bills;
		page.totalCount = count;
		page.conditions = conditionMap;

		return page;
 	}
 	
 	/**
 	 * 应付账单管理--本金垫付理财账单列表
 	 * @param id
 	 * @param currPage
 	 * @param info
 	 * @return
 	 */
 	public static PageBean<v_bill_invests_principal_advances> queryBillPrincipalAdvances(long supervisorId, String yearStr, String monthStr, 
 			String typeStr, String key, String orderTypeStr, String currPageStr, String pageSizeStr, ErrorInfo error) {
 		error.clear();
 		
 		int year = 0;
 		int month = 0;
 		int type = 0;
 		int orderType = 0;
 		int currPage = Constants.ONE;
 		int pageSize = Constants.TEN;
 		
 		if(NumberUtil.isNumericInt(yearStr)) {
 			year = Integer.parseInt(yearStr);
 		}
 		
 		if(NumberUtil.isNumericInt(monthStr)) {
 			month = Integer.parseInt(monthStr);
 		}
 		
 		if(NumberUtil.isNumericInt(typeStr)) {
 			type = Integer.parseInt(typeStr);
 		}
 		
 		if(NumberUtil.isNumericInt(orderTypeStr)) {
 			orderType = Integer.parseInt(orderTypeStr);
 		}
 		
 		if(NumberUtil.isNumericInt(currPageStr)) {
 			currPage = Integer.parseInt(currPageStr);
 		}
 		
 		if(NumberUtil.isNumericInt(pageSizeStr)) {
 			pageSize = Integer.parseInt(pageSizeStr);
 		}
 		
 		if(month < 0 || month > 12) {
 			month = 0;
 		}
 		
 		if(type < 0 || type > 3) {
 			type = 0;
 		}
 		
 		if(orderType < 0 || orderType > 10) {
 			orderType = 0;
 		}
 		
 		Map<String,Object> conditionMap = new HashMap<String, Object>();
		
		conditionMap.put("year", year);
		conditionMap.put("month", month);
		conditionMap.put("type", type);
		conditionMap.put("key", key);
		conditionMap.put("orderType", orderType);
 		
		StringBuffer conditions = new StringBuffer("1=1 ");
		List<Object> values = new ArrayList<Object>();
		
		if(year != 0 ) {
			conditions.append("and year = ? ");
			values.add(year);
		}
		
		if(month != 0) {
			conditions.append("and month = ? ");
			values.add(month);
		}
		
		if(StringUtils.isNotBlank(key)) {
			conditions.append(Constants.C_TYPE[type]);
			
			if(type == 0 ) {
				values.add("%"+key+"%");
				values.add("%"+key+"%");
				values.add("%"+key+"%");
			}else {
				values.add("%"+key+"%");
			}	
		}
		
		conditions.append("order by "+Constants.BILL_ORDER_REPAYMENT[orderType]);
		
		List<v_bill_invests_principal_advances> bills = new ArrayList<v_bill_invests_principal_advances>();
		int count = 0;
		
		try {
			count = (int)v_bill_invests_principal_advances.count(conditions.toString(), values.toArray());
			bills = v_bill_invests_principal_advances.find(conditions.toString(), values.toArray()).fetch(currPage, pageSize);
		}catch (Exception e) {
			e.printStackTrace();
			Logger.info("查询应付账单管理--本金垫付理财账单列表情况时："+e.getMessage());
			error.code = -1;
			error.msg = "由于数据库异常，查询应付账单管理--本金垫付理财账单列表情况失败";
			
			return null;
		}
		
		PageBean<v_bill_invests_principal_advances> page = new PageBean<v_bill_invests_principal_advances>();
		
		page.pageSize = pageSize;
		page.currPage = currPage;
		page.page = bills;
		page.totalCount = count;
		page.conditions = conditionMap;

		return page;
 	}
 	
 	/**
 	 * 应付账单管理--理财情况统计
 	 * @param id
 	 * @param currPage
 	 * @param info
 	 * @return
 	 */
 	public static PageBean<v_bill_invests_payables_statistics> queryBillInvestStatistics(int noPage, long supervisorId,
 			String yearStr, String monthStr, String orderTypeStr, String currPageStr, String pageSizeStr,
 			ErrorInfo error) {
 		error.clear();
 		
 		int year = 0;
 		int month = 0;
 		int orderType = 0;
 		int currPage = Constants.ONE;
 		int pageSize = Constants.TEN;
 		
 		if(NumberUtil.isNumericInt(yearStr)) {
 			year = Integer.parseInt(yearStr);
 		}
 		
 		if(NumberUtil.isNumericInt(monthStr)) {
 			month = Integer.parseInt(monthStr);
 		}
 		
 		
 		if(NumberUtil.isNumericInt(orderTypeStr)) {
 			orderType = Integer.parseInt(orderTypeStr);
 		}
 		
 		if(NumberUtil.isNumericInt(currPageStr)) {
 			currPage = Integer.parseInt(currPageStr);
 		}
 		
 		if(NumberUtil.isNumericInt(pageSizeStr)) {
 			pageSize = Integer.parseInt(pageSizeStr);
 		}
 		
 		if(month < 0 || month > 12) {
 			month = 0;
 		}
 		
 		if(orderType < 0 || orderType > 8) {
 			orderType = 0;
 		}
 		
 		Map<String,Object> conditionMap = new HashMap<String, Object>();
		
		conditionMap.put("year", year);
		conditionMap.put("month", month);
		conditionMap.put("orderType", orderType);
 		
		StringBuffer conditions = new StringBuffer("1=1 ");
		List<Object> values = new ArrayList<Object>();
		
		if(year != 0 ) {
			conditions.append("and year = ? ");
			values.add(year);
		}
		
		if(month != 0) {
			conditions.append("and month = ? ");
			values.add(month);
		}
		
		
		conditions.append("order by "+Constants.BILL_ORDER_INVEST[orderType]);
		
		List<v_bill_invests_payables_statistics> bills = new ArrayList<v_bill_invests_payables_statistics>();
		int count = 0;
		
		try {
			count = (int)v_bill_invests_payables_statistics.count(conditions.toString(), values.toArray());
			
			if(noPage == Constants.NO_PAGE){
				bills = v_bill_invests_payables_statistics.find(conditions.toString(), values.toArray()).fetch();
			}else{
				bills = v_bill_invests_payables_statistics.find(conditions.toString(), values.toArray()).fetch(currPage, pageSize);
			}
		}catch (Exception e) {
			e.printStackTrace();
			Logger.info("查询应收账单管理--应收款借款账单统计表情况时："+e.getMessage());
			error.code = -1;
			error.msg = "由于数据库异常，查询应收账单管理--应收款借款账单统计表情况失败";
			
			return null;
		}
		
		PageBean<v_bill_invests_payables_statistics> page = new PageBean<v_bill_invests_payables_statistics>();
		
		page.pageSize = pageSize;
		page.currPage = currPage;
		page.page = bills;
		page.totalCount = count;
		page.conditions = conditionMap;

		return page;
 	}
 	
 	/**
	 * 账单提醒--最新的还款账单(借款子账户首页)
	 * @param error 信息值
	 * @return 
	 */
	public static List<v_bill_recently_pending> queryRecentlyBills(ErrorInfo error){
		error.clear();
		
		User user = User.currUser();
		
		try {
			return v_bill_recently_pending.find("user_id = ? order by repay_time", user.id).fetch(Constants.RECENTLY_REPAYMENT_BILL_COUNT);
		} catch (Exception e) {
			Logger.error("账单提醒--最新的还款账单:" + e.getMessage());
			error.msg = error.FRIEND_INFO  + "加载最新的还款账单列表失败!";
			
			return null;
		}
	}
	
	/**
	 * 账单提醒--推送
	 * @param error 信息值
	 * @return 
	 */
	public static void queryRecentlyBillsForCast(String period){
		List<v_bill_recently_pending> bills = null;
		
		try {
			bills =  v_bill_recently_pending.find("repay_time = ?", period).fetch();
		} catch (Exception e) {
			e.printStackTrace();
			Logger.error("借款账单提醒--推送:" + e.getMessage());
			
			return ;
		}
		
		if(bills == null || bills.size() == 0) {
			return ;
		}
		
		for(v_bill_recently_pending bill : bills) {
			if(bill.device_type != 1 && bill.device_type != 2) {
				return ;
			}
			
			if(bill.is_bill_push) {
				String device = bill.device_type == 1 ? "\"custom_content\":{\"billId\":\""+bill.sign+"\",\"type\":\"2\"}" : "\"aps\": {\"alert\":\"test\",\"sound\":\"1\",\"badge\":\"1\"},\"billId\":\""+bill.sign+"\",\"type\":\"2\"";
				device = "{\"title\":\"借款账单提醒通知\",\"description\":\"你有一条新的借款账单提醒通知\","+ device + "}";
				PushMessage.pushUnicastMessage(bill.device_user_id, bill.channel_id, bill.device_type, device);
			}
			
		}
	}
	
	/**
	 * 还款计算器明细
	 */
	public static List<Map<String, Object>> repaymentCalculate(double amount, double apr, int period, int periodUnit, int repaymentType){
		double monthRate = 0;//月利率
 		double monPay = 0;//每个月要还的金额
 		double monPayInterest = 0;// 每个月利息（如果是天标的话，就是所有的利息）
 		double monPayAmount = 0;//每个月本金
 		double totalAmount = 0;//总共要还的金额
 		double payRemain = 0;//剩余要还的金额
 		double payAmount = 0;//加起来付了多少钱
 		double totalInterest = 0;//总利息
 		double payAmountAdd = 0;
 		double stillPay = 0;
 		
 		int deadline = period; //借款标期限
 		double borrowSum = amount; //借款金额
 		monthRate = Double.valueOf(apr * 0.01)/12.0;//通过年利率得到月利率
 		Map<String, Object> payMap =null;
 		List<Map<String, Object>> payList = null;
 		
 		 //按日结算
        if(periodUnit == 1){
 			   
 		  monPayInterest = Arith.mul(borrowSum, monthRate);
 		  totalInterest = monPayInterest * deadline / 30;
 		  totalAmount = borrowSum + totalInterest;
 		  payList = new ArrayList<Map<String, Object>>();	
 		  payMap = new HashMap<String, Object>();
 		  
 		  payMap.put("period", 1);
 		  payMap.put("monPay", Arith.round(totalAmount, 2));
 		  payMap.put("monPayAmount", Arith.round(borrowSum, 2));
 		  payMap.put("monPayInterest", Arith.round(totalInterest, 2));
 		  payMap.put("stillPay", Arith.round(stillPay, 2));
 		  payList.add(payMap);
 	   
 	      return payList;
 	    }else{
			//按月还款、等额本息
			if (repaymentType == Constants.PAID_MONTH_EQUAL_PRINCIPAL_INTEREST) {
	
				if (periodUnit == -1 || periodUnit == 0) {//判断标类(年标，月标，天标)
	
					if (periodUnit == -1) {//如果为年标，那么传过来的借款期限都乘以12
						deadline = deadline * 12;
					}
	
					monPay = Double.valueOf(Arith.mul(borrowSum, monthRate) * Math.pow((1 + monthRate), deadline))/ 
					Double.valueOf(Math.pow((1 + monthRate), deadline) - 1);//每个月要还的本金和利息
					monPay = Arith.round(monPay, 2);
					amount = borrowSum;
					totalAmount = Arith.mul(monPay, deadline);//总共要还的金额
					payRemain = Arith.round(totalAmount, 2);
					payList = new ArrayList<Map<String, Object>>();
					
					for (int n = 1; n <= deadline; n++) {
						monPayInterest = Arith.round(Arith.mul(amount, monthRate), 2);// 每个月利息
						monPayAmount = Arith.round(Arith.sub(monPay, monPayInterest), 2);// 每个月本金
						amount = Arith.round(Arith.sub(amount, monPayAmount), 2);
						
						if (n == deadline) {
							monPay = payRemain;
							monPayAmount = borrowSum - payAmount;
							monPayInterest = monPay - monPayAmount;
						}
						
						payAmount += monPayAmount;
						payRemain = Arith.sub(payRemain, monPay);
	
						payAmountAdd += monPayAmount + monPayInterest;
						stillPay = totalAmount - payAmountAdd;
						payMap = new HashMap<String, Object>();
						
						payMap.put("period", n);
						payMap.put("monPay", monPay);
						payMap.put("monPayAmount", Arith.round(monPayAmount, 2));
						payMap.put("monPayInterest", Arith.round(monPayInterest, 2));
						payMap.put("stillPay", Arith.round(stillPay, 2));
						payList.add(payMap);
				} 
			}
				return payList;
		}
			
	 		//按月付息、一次还款
	        if(repaymentType == Constants.PAID_MONTH_ONCE_REPAYMENT){
	        	monPayInterest = Arith.round(Arith.mul(borrowSum, monthRate), 2);
	        	double payInterestAdd = 0;
	        	  
	        	  if (periodUnit == -1 || periodUnit == 0) {
	
	  				  if (periodUnit == -1) {
	  					deadline = deadline * 12;
	  				  }
	  				  
	  				 totalInterest =  Arith.round(Arith.mul(Arith.mul(borrowSum, monthRate),deadline),2);
	  				 payList = new ArrayList<Map<String, Object>>();
	  				  
					  for(int n = 1;n<=deadline;n++){
						  
						  if(n == deadline){
							  monPayAmount = borrowSum;
							  double realPayInterest = Arith.round(Arith.mul(Arith.mul(borrowSum, monthRate), deadline), 2);//正真要还的利息金额 
							  monPayInterest = realPayInterest - payInterestAdd;//最后一期纠偏要还的利息 
						  }
						  else{
							  monPayAmount = 0.00;
						  }
						  
						  payInterestAdd += monPayInterest; 
						  payAmountAdd += monPayAmount + monPayInterest;
						  stillPay = totalInterest + amount - payAmountAdd;
						  monPay = monPayAmount + monPayInterest;
						  payMap = new HashMap<String, Object>();
							
						  payMap.put("period", n);
						  payMap.put("monPay",Arith.round(monPay, 2));
						  payMap.put("monPayAmount", Arith.round(monPayAmount, 2));
						  payMap.put("monPayInterest", Arith.round(monPayInterest, 2));
						  payMap.put("stillPay", Arith.round(stillPay, 2));
						  payList.add(payMap);
					  }
	        	  }
	        	  
	        	  return payList;
	  			}
	
	        //一次还款
	       if(repaymentType == Constants.ONCE_REPAYMENT){
	    	   if (periodUnit == -1 || periodUnit == 0) {
				   if (periodUnit == -1) {
						deadline = deadline * 12;
					}
				   
				  monPayInterest = Arith.mul(borrowSum, monthRate);
				  totalInterest = monPayInterest * deadline;
				  totalAmount = borrowSum + totalInterest;
				  payList = new ArrayList<Map<String, Object>>();	
				  payMap = new HashMap<String, Object>();
				  
				  payMap.put("period", 1);
				  payMap.put("monPay", Arith.round(totalAmount, 2));
				  payMap.put("monPayAmount", Arith.round(borrowSum, 2));
				  payMap.put("monPayInterest", Arith.round(totalInterest, 2));
				  payMap.put("stillPay", Arith.round(stillPay, 2));
				  payList.add(payMap);
	    	   } 
	    	   
	    	   return payList;
		    }
 	  }
       
        return null;
	}
	
	/**
	 * 计算标的已还本金
	 */
	public static double queryHasPayed(long bidId, ErrorInfo error){
		error.clear();
		
		String sql = "select sum(repayment_corpus+repayment_interest+overdue_mark) from t_bills where status in(-3,0) and bid_id = ?";
		Double amount = null;
		try {
			amount = t_bills.find(sql, bidId).first();
		}catch (Exception e) {
			e.printStackTrace();
			Logger.info("查询标的已还本金情况时："+e.getMessage());
			error.code = -1;
			error.msg = "由于数据库异常，查询标的已还本金情况失败";
			
			return 0;
		}
		
		return amount = null == amount ? 0 : amount.doubleValue();
	}
	
	/**
	 * 计算债权转让的债权额（汇付用到）
	 * @param userId 用户id
	 * @param investId 理财账单标的invest_id
	 * @param error
	 * @return
	 */
	public static double queryDebtAmount(long userId,  long investId, ErrorInfo error){
		error.clear();
	    
		/**
		 * 债权面额 = 投资本金 - 已收金额 
		 * 
		 * 
		 */
		String sql = "SELECT (invest.amount - ifnull((SELECT SUM(t.receive_corpus + t.receive_interest + t.overdue_fine) "
				+ "FROM t_bill_invests t WHERE t.invest_id = ? AND status in (?, ?, ?)) ,0))"
				+ "FROM t_invests AS invest  WHERE id = ?";

		Query query = JPA.em().createNativeQuery(sql)
				.setParameter(1, investId)
				.setParameter(2, Constants.NORMAL_RECEIVABLES) 
				.setParameter(3, Constants.ADVANCE_PRINCIIPAL_RECEIVABLES)
				.setParameter(4, Constants.OVERDUE_RECEIVABLES)
				.setParameter(5, investId); 
		Object obj = null;
	
		if(query.getResultList().size() == 0){
			return 0;
		}
		
		try {
			obj = query.getResultList().get(0);
		} catch (Exception e) {
			e.printStackTrace();
			Logger.error("计算债权转让的债权额时："+e.getMessage());
			error.code = -1;
			error.msg = "计算债权转让的债权额异常！";
			
			return 0;
		}
		
		return (obj == null) ? 0 : Convert.strToDouble(obj+"", 0);
	}
	
	public static void updateMerBillNo(long id, String merBillNo, ErrorInfo error) {
		error.clear();
		String sql = "update t_bills set mer_bill_no = ? where id = ?";
		Query query = JPA.em().createNativeQuery(sql);
		query.setParameter(1, merBillNo);
		query.setParameter(2, id);
		int rows = 0;
		
		try {
			rows = query.executeUpdate();
		} catch (Exception e) {
			JPA.setRollbackOnly();
			Logger.info(e.getMessage());
			error.code = -1;
			error.msg = "数据库异常";
		}
		
		if (rows == 0) {
			JPA.setRollbackOnly();
			error.code = -1;
			error.msg = "数据未更新";
		}
	}
	
	/**
	 * 本金垫付后线下收款
	 */
	public void offlineReceive(ErrorInfo error) {
		error.clear();
		
		if (this.status != Constants.ADVANCE_PRINCIIPAL_REPAYMENT) {
			error.code = -1;
			error.msg = "还未进行本金垫付";
			
			return;
		}
		
		//1. 更新借款账单状态
		String sql = "update t_bills set status = ? where id = ?";
		Query query = JPA.em().createQuery(sql).setParameter(1, Constants.OVERDUE_PATMENT).setParameter(2, this.id);
		int rows = 0;
		
		try {
			rows = query.executeUpdate();
		} catch(Exception e) {
			Logger.info(e.getMessage());
			error.code = -1;
			error.msg = "数据库异常";
			JPA.setRollbackOnly();
			
			return;
		}
		
		if (rows < 1) {
			error.code = -1;
			error.msg = "数据未更新";
			
			return;
		}
		
		//2. 平台保障金资金减少（线下收款）
		double payment = this.repaymentCorpus + this.repaymentInterest + this.overdueFine;// 总共要还款的金额
		DealDetail.addPlatformDetail(DealType.OFFLINE_PAY_FEE, this._id, -1, this.bid.userId, DealType.ACCOUNT, payment, 2, "平台支出第"+this._id+"号账单本金垫付后线下收款操作金额", error);
		
		if(error.code < 0) {
			JPA.setRollbackOnly();
			
			return;
		}
		
		//逾期还款减去信用积分
	    int creditScore = DealDetail.addCreditScore(this.bid.userId, -1, 0, this._id, "第"+this._id+"号账单逾期还款扣除信用积分", error);
		
		Map<String, Object> userMap = new HashMap<String,Object>();
			
		String userSql = "select new Map(name as name, email as eamil, mobile as mobile) from t_users where id = ? ";
		
		try {
			userMap = t_users.find(userSql, this.bid.userId).first();
		}catch(Exception e) {
			e.printStackTrace();
			Logger.info("根据用户id查询时："+e.getMessage());
			error.code = -1;
			error.msg = "查询用户名失败";
			
			return;
		}
		
		String userName = (String)userMap.get("name");
		String userEamil = (String)userMap.get("eamil");
		String userMobile = (String)userMap.get("mobile");
		
		//发送站内信  尊敬的username:\n [date] 您在晓风安全网贷系统6对标的【title】还款了￥needSum 元
		TemplateStation station = new TemplateStation();
		station.id = Templets.M_SUCCESS_PAY;
		
		if(station.status) {
			String mContent = station.content.replace("userName", userName);
			mContent = mContent.replace("date",DateUtil.dateToString(this.bid.time));
			mContent = mContent.replace("title",this.bid.title);
			mContent = mContent.replace("needSum",  DataUtil.formatString((repaymentCorpus +  repaymentInterest + this.overdueFine)));
			mContent = mContent.replace("creditScore",-creditScore+"");
			
			StationLetter letter = new StationLetter();
			letter.senderSupervisorId = 1;
			letter.receiverUserId = this.bid.userId;
			letter.title = station.title;
			letter.content = mContent;
			 
			letter.sendToUserBySupervisor(error);
		}
		
		//发送邮件 尊敬的username:\n [date] 您在晓风安全网贷系统6对标的【title】还款了￥needSum 元
		TemplateEmail email = new TemplateEmail();
		email.id = Templets.E_SUCCESS_PAY;
		
		if(email.status) {
			String eContent = email.content.replace("userName", userName);
			eContent = eContent.replace("date",DateUtil.dateToString(this.bid.time));
			eContent = eContent.replace("title",this.bid.title);
			eContent = eContent.replace("needSum",  DataUtil.formatString((repaymentCorpus +  repaymentInterest + this.overdueFine)));
			eContent = eContent.replace("creditScore",-creditScore+"");
			
			email.addEmailTask(userEamil, email.title, eContent);
		}
		
		//发送短信  尊敬的userName: 你申请的编号bidId借款标repayPeriod期成功还款，还款金额￥needSum元，
		TemplateSms sms = new TemplateSms();
		BackstageSet backstageSet = BackstageSet.getCurrentBackstageSet();
		sms.id = Templets.S_REPAY_SUCCESS;
		if(sms.status && StringUtils.isNotBlank(userMobile)) {
			String eContent = sms.content.replace("userName", userName);
			eContent = eContent.replace("bidId",backstageSet.loanNumber+bidId);
			eContent = eContent.replace("repayPeriod",this.periods+"");
			eContent = eContent.replace("needSum",DataUtil.formatString((repaymentCorpus +  repaymentInterest + this.overdueFine)));
			TemplateSms.addSmsTask(userMobile, eContent);
		}
		
		//3.判断这个借款标是否已还完款，若还完标记这个借款标为已还款完状态
		if(this.isEndPayment(bidId, error) == 0){
			sql = "update t_bids set status = ?, last_repay_time = ? where id = ?";
			query = JPA.em().createQuery(sql).setParameter(1, Constants.BID_REPAYMENTS)
					.setParameter(2, DateUtil.currentDate()).setParameter(3, this.bidId);

			try {
				rows = query.executeUpdate();
			} catch(Exception e) {
				Logger.info(e.getMessage());
				error.code = -1;
				error.msg = "数据库异常";
				JPA.setRollbackOnly();
				
				return;
			}
			
			if (rows < 1) {
				error.code = -1;
				error.msg = "数据未更新";
				
				return;
			}
		}
		
		error.code = 0;
		error.msg = "线下收款成功";
	}
	
	/**
	 * 2014-12-29 限制还款需要从第一期逐步开始还款 
	 */
	public static int checkPeriod(long bidId, int periods) {
		try {
			return (int) t_bills.count("status in(?, ?) and bid_id = ? and periods < ?",
					Constants.NO_REPAYMENT, Constants.ADVANCE_PRINCIIPAL_REPAYMENT,
					bidId, periods);
		} catch (Exception e) {
			return 1;
		}
	}
	
	/**
	 * 获取收款、本金垫付订单号
	 * @param error
	 * @param id
	 * @return
	 */
	public static String getMerBillNo(ErrorInfo error, long id) {
		error.code = -1;
		Query query = JpaHelper.execute("select mer_bill_no from t_bills where id = ?", id);
		String pMerBillNo = null;
		
		try {
			pMerBillNo = (String) query.getSingleResult();
		} catch (Exception e) {
			Logger.error(e.getMessage());
			error.code = -1;
			error.msg = "数据库异常";
			
			return null;
		}
		
		//定单号存在，则直接返回
		if (StringUtils.isNotBlank(pMerBillNo)) {
			error.code = 1;
			
			return pMerBillNo;
		}
		
		pMerBillNo = User.createBillNo();
		JPAUtil.executeUpdate(error, "update t_bills set mer_bill_no = ? where id = ?", pMerBillNo, id);
		
		if (error.code < 0) {
			JPA.setRollbackOnly();
			
			return null;
		}
		
		error.code = 1;
		
		return pMerBillNo;
	}
	
	/**
	 * 获取还款订单号
	 * @param error
	 * @param id
	 * @return
	 */
	public static String getRepaymentBillNo(ErrorInfo error, long id) {
		error.code = -1;
		Query query = JpaHelper.execute("select repayment_bill_no from t_bills where id = ?", id);
		String pRepaymentBillNo = null;
		
		try {
			pRepaymentBillNo = (String) query.getSingleResult();
		} catch (Exception e) {
			Logger.error(e.getMessage());
			error.code = -1;
			error.msg = "数据库异常";
			
			return null;
		}
		
		//定单号存在，则直接返回
		if (StringUtils.isNotBlank(pRepaymentBillNo)) {
			error.code = 1;
			
			return pRepaymentBillNo;
		}
		
		pRepaymentBillNo = User.createBillNo();
		JPAUtil.executeUpdate(error, "update t_bills set repayment_bill_no = ? where id = ?", pRepaymentBillNo, id);
		
		if (error.code < 0) {
			JPA.setRollbackOnly();
			
			return null;
		}
		
		error.code = 1;
		
		return pRepaymentBillNo;
	}
	
	/**
	 * 查询标的本金利息合计
	 * @param bidId
	 * @param error
	 * @return
	 */
	public static double queryBidPrincipal (long bidId, ErrorInfo error){
		String sql = "select sum(repayment_corpus + repayment_interest + overdue_fine) as principal from t_bills where bid_id = ?";
		
		List<Map<String, Object>> list = null;
		
		list = JPAUtil.getList(error, sql, bidId);
		
		if(list.size() == 0){
			return 0;
		}
		
		Object obj = list.get(0).get("principal");
		
		return (obj == null) ? 0 : Convert.strToDouble(obj+"", 0);
	}
	
	/**
	 * 更新资金托管交易状态
	 * @param billId  借款账单id
	 * @param ipsStatus  交易状态
	 * @param currentStatus  当前必须是currentStatus状态，才能执行更新
	 */
	public static void updateIPSStatusByID(long billId, int ipsStatus, int currentStatus){
		String sql = "update t_bills set ips_status = ? where id = ? and ips_status = ?";
		Query query = JPA.em().createQuery(sql).setParameter(1, ipsStatus).setParameter(2, billId).setParameter(3, currentStatus);

		try {
			query.executeUpdate();
		} catch (Exception e) {
			Logger.error("更新资金托管交易(bills)状态("+ currentStatus +"-->"+ ipsStatus +")时，%s", e.getMessage());
		}
	}
	
	/**
	 * 查询资金托管交易状态
	 * @param billId  借款账单id
	 */
	public static int QueryIPSStatusByID(long billId){
		String sql = "select ips_status from t_bills where id = ?";
		
		int ipsStatus = 0;
		
		try {
			ipsStatus = t_bills.find(sql, billId).first();
		} catch (Exception e) {
			Logger.error("更新资金托管交易(bills)状态时，%s", e.getMessage());
		}
		
		return ipsStatus;
	}
	
	/**
	 * 平台累计收益
	 */
	public static double  sumIncome() {
		// 投资收益(收益+逾期)
		String sql = "select ifnull(sum(repayment_interest),0) + ifnull(sum(overdue_fine),0) + ifnull(sum(real_repayment_interest),0) from t_bills";
		double d = t_bills.find(sql).first();
		// cps收益 + 投标奖励
		sql = "select ifnull(sum(amount),0) + 0.0 from t_user_details where operation in (6,51,52,107)";
		double d1 = t_user_details.find(sql).first();
		return d + d1;
	}

	/**
	 * 查询到期还款金额
	 * @param error
	 * @return
	 */
	public static double queryNextMonthRepaymentSum(ErrorInfo error){
		error.clear();
		
		Object result = null;
		
		String sql = "select SUM(repayment_corpus) + SUM(repayment_interest) + SUM(overdue_fine) AS repayment from t_bills where status=-1 AND (YEAR (repayment_time) = YEAR (now())) AND (MONTH (repayment_time) = MONTH (now())) ";
		Query query = JPA.em().createNativeQuery(sql);
		
		try {
            result = query.getSingleResult();
            
		} catch (Exception e) {
			Logger.error("本月到期还款金额:" + e.getMessage());
			error.code = -1;
			error.msg = "查询本月到期还款金额";
			
			return 0;
		}
		
		return result==null?0:(Double)result;
	}
	
	/**
	 *更新 理财账单赎回审核状态
	 * @param error
	 * @return
	 */
	public static void updateBillStatus(long id ,ErrorInfo error){
		error.clear();
		int row =0;
	    String loan_sql ="update t_bill_invests set status =? where id=?  ";

		Query query = JPA.em().createQuery(loan_sql);
		query.setParameter(1, 1);//状态
		query.setParameter(2, id);// 人
		try {
			row = query.executeUpdate();
		} catch (Exception e) {
			e.printStackTrace();
			Logger.error("定期标->赎回审核:" + e.getMessage());
			error.msg = "定期标->赎回审核 修改状态失败!";
			JPA.setRollbackOnly();

		}
		if(row<1){
			JPA.setRollbackOnly();
			error.msg="定期标->赎回审核 修改状态出错！";		
		}
	}
	
	/**
	 *更新 理财账单实际收利息
	 * @param error
	 * @return
	 */
	public static void updateInvestBillRate(double personDayRate ,Invest invest ,ErrorInfo error){
		error.clear();
		
		int row =0;
		EntityManager em = JPA.em();
		String updateSql = "update t_bill_invests set real_receive_interest = real_receive_interest+?" +
 				"  where user_id = ?  and status not in (?,?) and invest_id = ? ";
 		
        Query query = em.createQuery(updateSql).setParameter(1, personDayRate).setParameter(2, invest.userId)
        .setParameter(3, Constants.NORMAL_RECEIVABLES).setParameter(4, Constants.FOR_DEBT_MARK).setParameter(5, invest.id);
        
        try {
        	row = query.executeUpdate();
		} catch(Exception e) {
			e.printStackTrace();
			Logger.info("修改投资账单的实际收入利息情况时："+e.getMessage());
			error.code = -6;
			error.msg = "修改投资账单的实际收入利息情况失败";
			JPA.setRollbackOnly();
		}
		
		if(row == 0){
			error.code = -1;
			error.msg = "修改投资账单的实际收入利息情况失败";
			JPA.setRollbackOnly();
		}
	}
}