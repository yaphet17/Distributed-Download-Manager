package Sample;

public class Sample {


    public static void main(String args[]){

        for(int i=0;i<=100;i++){
            System.out.print("\rDownloading|");
            for(int j=1;j<=i;j++){
                System.out.print("#");
            }
            for(int k=i;k<100;k++){
                System.out.print(" ");
            }
            System.out.print("|"+i+"%");

        }

    }
}
